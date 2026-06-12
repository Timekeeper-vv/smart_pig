package com.example.shixun.mapper;

import com.example.shixun.model.MedicationRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface MedicationMapper {
    List<MedicationRecord> findAll();
    List<MedicationRecord> findByEarTag(String earTag);
    int insert(MedicationRecord record);
    int deleteById(Long id);
    List<MedicationRecord> findPage(@Param("search") String search, @Param("drug") String drug, @Param("dateFrom") String dateFrom, @Param("dateTo") String dateTo, @Param("offset") int offset, @Param("size") int size);
    long countSearch(@Param("search") String search, @Param("drug") String drug, @Param("dateFrom") String dateFrom, @Param("dateTo") String dateTo);
}
