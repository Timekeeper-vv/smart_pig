package com.example.shixun.mapper;

import com.example.shixun.model.SlaughterRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SlaughterMapper {
    List<SlaughterRecord> findAll();
    List<SlaughterRecord> findByEarTag(String earTag);
    int insert(SlaughterRecord record);
    int deleteById(Long id);
    List<SlaughterRecord> findPage(@Param("search") String search, @Param("offset") int offset, @Param("size") int size);
    long countSearch(@Param("search") String search);
}
