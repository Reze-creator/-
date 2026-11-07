package com.parking.common;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.function.Function;

public record Pages<T>(List<T> items, int page, int size, long total) {
  public static <E, T> Pages<T> select(
      BaseMapper<E> mapper, QueryWrapper<E> query, int page, int size, Function<E, T> view) {
    if (page < 1 || size < 1 || size > 100)
      throw BusinessException.invalid("page必须≥1，size必须为1..100");
    long total = mapper.selectCount(query);
    query.orderByDesc("created_at", "id").last("LIMIT " + size + " OFFSET " + ((page - 1L) * size));
    return new Pages<>(mapper.selectList(query).stream().map(view).toList(), page, size, total);
  }
}
