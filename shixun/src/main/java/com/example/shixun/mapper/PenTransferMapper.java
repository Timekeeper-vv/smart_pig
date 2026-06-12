package com.example.shixun.mapper;

import com.example.shixun.model.PenTransferRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface PenTransferMapper {
    List<PenTransferRecord> findAll();
    List<PenTransferRecord> findByEarTag(String earTag);
    int insert(PenTransferRecord record);
    int deleteById(Long id);
    List<PenTransferRecord> findPage(@Param("search") String search, @Param("offset") int offset, @Param("size") int size);
    long countSearch(@Param("search") String search);
}
