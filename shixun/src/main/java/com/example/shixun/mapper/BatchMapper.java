package com.example.shixun.mapper;

import com.example.shixun.model.Batch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface BatchMapper {
    List<Batch> findAll();
    Batch findById(Long id);
    Batch findByBatchCode(String batchCode);
    int insert(Batch batch);
    int update(Batch batch);
    int deleteById(Long id);
    int countByBatchCodePrefix(String prefix);
    List<Batch> findPage(@Param("search") String search, @Param("offset") int offset, @Param("size") int size);
    long countSearch(@Param("search") String search);
}
