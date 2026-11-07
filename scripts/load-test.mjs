/** 仅对隔离的 parking_test 实例运行；不使用业务库、不依赖第三方压测库。 */
import { performance } from 'node:perf_hooks'
import { mkdir, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
const base = process.argv[2] || 'http://127.0.0.1:8091'
const requests = Number(process.argv[3] || 500)
const concurrency = Number(process.argv[4] || 20)
if (!Number.isInteger(requests) || requests < 1 || requests > 10000 || !Number.isInteger(concurrency) || concurrency < 1 || concurrency > 100) throw new Error('Invalid load parameters')
async function request(path, method = 'GET', body, token) {
  const response = await fetch(`${base}/api${path}`, { method, headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) }, body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(15000) })
  return { status: response.status, ...(await response.json()) }
}
async function parallel(count, action, workers = concurrency) {
  let cursor = 0
  const results = new Array(count)
  await Promise.all(Array.from({ length: Math.min(workers, count) }, async () => { while (cursor < count) { const index = cursor++; results[index] = await action(index) } }))
  return results
}
const startAt = new Date((Math.floor(Date.now() / 1800000) + 60) * 1800000).toISOString()
const endAt = new Date(new Date(startAt).getTime() + 3600000).toISOString()
const query = `/resources?${new URLSearchParams({ startAt, endAt })}`
await parallel(20, () => request(query), 4)
const started = performance.now()
const samples = await parallel(requests, async () => {
  const begin = performance.now()
  try { const result = await request(query); return { status: result.status, ms: performance.now() - begin } }
  catch { return { status: 0, ms: performance.now() - begin } }
})
const elapsedMs = performance.now() - started
const latencies = samples.map((x) => x.ms).sort((a, b) => a - b)
const percentile = (ratio) => latencies[Math.min(latencies.length - 1, Math.ceil(latencies.length * ratio) - 1)]
const statuses = {}
for (const sample of samples) statuses[sample.status] = (statuses[sample.status] || 0) + 1

// 写入场景检查业务正确性；不把预期的 409 冲突计作接口崩溃。
const username = `bench_${crypto.randomUUID().slice(0, 12).replaceAll('-', '')}`
const account = await request('/auth/register', 'POST', { username, password: `Bench@${crypto.randomUUID()}`, displayName: '隔离压测账号' })
if (account.status !== 201) throw new Error('Cannot create test account')
const token = account.data.token
const resources = (await request(query)).data
const resource = resources.find((item) => item.available)
if (!resource) throw new Error('No test resource available')
const payload = { resourceId: resource.id, plateNumber: '浙A99999', startAt, endAt }
const created = []
let correctness
try {
  const competition = await parallel(20, (index) => request('/bookings', 'POST', { ...payload, requestId: `${username}_race_${index}` }, token))
  for (const result of competition) if (result.status === 200) created.push(result.data.id)
  const success = competition.filter((x) => x.status === 200).length
  const conflict = competition.filter((x) => x.status === 409).length
  if (success !== 1 || conflict !== 19) throw new Error(`Reservation invariant failed: ${success}/${conflict}`)
  await request(`/bookings/${created[0]}/cancel`, 'POST', undefined, token)
  const idempotent = await parallel(20, () => request('/bookings', 'POST', { ...payload, requestId: `${username}_same` }, token))
  const ids = new Set(idempotent.filter((x) => x.status === 200).map((x) => x.data.id))
  for (const id of ids) created.push(id)
  if (ids.size !== 1 || idempotent.some((x) => x.status !== 200)) throw new Error('Idempotency invariant failed')
  const id = [...ids][0]
  const paid = await parallel(20, () => request(`/bookings/${id}/pay`, 'POST', undefined, token))
  if (paid.some((x) => x.status !== 200 || x.data.status !== 'CONFIRMED')) throw new Error('Payment invariant failed')
  correctness = { concurrentReservations: 20, created: success, expectedConflicts: conflict, idempotentRequests: 20, uniqueBookingIds: ids.size, repeatedPayments: paid.length, passed: true }
} finally {
  for (const id of created) await request(`/bookings/${id}/cancel`, 'POST', undefined, token)
}
const report = { measuredAt: new Date().toISOString(), baseUrl: base, database: 'parking_test', node: process.version, scenario: 'time-window resource reads; 20 warmup requests excluded', requests, concurrency, elapsedMs, throughputPerSecond: requests / (elapsedMs / 1000), latencyMs: { p50: percentile(.5), p95: percentile(.95), p99: percentile(.99), max: latencies.at(-1) }, statuses, correctness, limitations: 'Single local process and local MySQL, small seeded dataset; not production capacity. HTTP latency includes local network and client scheduling.' }
const folder = fileURLToPath(new URL('../docs/performance/', import.meta.url))
await mkdir(folder, { recursive: true })
await writeFile(`${folder}/latest.json`, JSON.stringify(report, null, 2))
console.log(JSON.stringify(report, null, 2))
if (samples.some((x) => x.status !== 200)) process.exitCode = 1
