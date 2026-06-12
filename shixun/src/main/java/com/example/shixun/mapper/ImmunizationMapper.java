package com.example.shixun.mapper;

import com.example.shixun.model.ImmunizationRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ImmunizationMapper {
    List<ImmunizationRecord> findAll();
    List<ImmunizationRecord> findByEarTag(String earTag);
    int insert(ImmunizationRecord record);
    int deleteById(Long id);
    List<ImmunizationRecord> findPage(@Param("search") String search, @Param("vaccine") String vaccine, @Param("dateFrom") String dateFrom, @Param("dateTo") String dateTo, @Param("offset") int offset, @Param("size") int size);
    long countSearch(@Param("search") String search, @Param("vaccine") String vaccine, @Param("dateFrom") String dateFrom, @Param("dateTo") String dateTo);
}
