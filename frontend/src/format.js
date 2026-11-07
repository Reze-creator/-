export const bookingLabels = {
  PENDING_PAYMENT: '待支付',
  CONFIRMED: '预约成功',
  IN_USE: '使用中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  EXPIRED: '已过期',
}

export function money(value) {
  return Number(value || 0).toFixed(2)
}

export function localDate(value = new Date()) {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(value))
}

export function dateTime(value) {
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(value))
}

export function timeOnly(value) {
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(value))
}

export function windowFromInputs(date, startTime, endTime) {
  const start = new Date(`${date}T${startTime}:00+08:00`)
  const end = new Date(`${date}T${endTime}:00+08:00`)
  if (!Number.isFinite(start.getTime()) || !Number.isFinite(end.getTime()))
    throw new Error('请选择完整的预约时间')
  if (end <= start) throw new Error('结束时间需要晚于开始时间；界面暂支持同日预约')
  if (start <= new Date()) throw new Error('开始时间需要晚于当前时间')
  return { startAt: start.toISOString(), endAt: end.toISOString(), hours: (end - start) / 3600000 }
}

export const timeOptions = Array.from(
  { length: 48 },
  (_, index) => `${String(Math.floor(index / 2)).padStart(2, '0')}:${index % 2 ? '30' : '00'}`,
)
