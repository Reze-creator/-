package com.parking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.parking.entity.BookingEntity;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BookingMapper extends BaseMapper<BookingEntity> {
  /** 查询与[start,end)重叠的有效占用；过期的待支付预约立即排除。 */
  @Select(
      """
      <script>
      SELECT DISTINCT resource_id
      FROM booking
      WHERE start_at &lt; #{end}
        AND end_at &gt; #{start}
        AND (status IN ('CONFIRMED', 'IN_USE')
             OR (status = 'PENDING_PAYMENT' AND hold_until &gt; #{now}))
      <if test="resourceId != null">
        AND resource_id = #{resourceId}
      </if>
      </script>
      """)
  List<String> findBusyResourceIds(
      @Param("resourceId") String resourceId,
      @Param("start") Instant start,
      @Param("end") Instant end,
      @Param("now") Instant now);

  @org.apache.ibatis.annotations.Select("SELECT * FROM booking WHERE id = #{id} FOR UPDATE")
  BookingEntity lock(String id);

  @org.apache.ibatis.annotations.Update(
      "UPDATE booking SET status = 'EXPIRED', updated_at = #{now} WHERE status = 'PENDING_PAYMENT' AND hold_until <= #{now}")
  int expireDue(java.time.Instant now);
}
