package com.example.shixun.service;

import com.example.shixun.mapper.AnimalMapper;
import com.example.shixun.mapper.PenMapper;
import com.example.shixun.model.Animal;
import com.example.shixun.model.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class AnimalService {

    private final AnimalMapper animalMapper;
    private final PenMapper penMapper;

    public AnimalService(AnimalMapper animalMapper, PenMapper penMapper) {
        this.animalMapper = animalMapper;
        this.penMapper = penMapper;
    }

    public List<Animal> findAll() {
        return animalMapper.findAll();
    }

    public Animal findById(Long id) {
        return animalMapper.findById(id);
    }

    public Animal findByEarTag(String earTag) {
        return animalMapper.findByEarTag(earTag);
    }

    public String generateEarTag() {
        String prefix = "ET" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int count = animalMapper.countByEarTagPrefix(prefix);
        String candidate = prefix + String.format("%03d", count + 1);
        while (animalMapper.existsByEarTag(candidate)) {
            count++;
            candidate = prefix + String.format("%03d", count + 1);
        }
        return candidate;
    }

    @Transactional
    public Animal create(Animal animal) {
        if (animal.getEarTag() == null || animal.getEarTag().isBlank()) {
            animal.setEarTag(generateEarTag());
        } else if (animalMapper.existsByEarTag(animal.getEarTag())) {
            throw new IllegalArgumentException("耳标号已存在: " + animal.getEarTag());
        }
        animalMapper.insert(animal);
        if (animal.getCurrentPenId() != null) {
            penMapper.incrementCount(animal.getCurrentPenId());
        }
        return animalMapper.findByEarTag(animal.getEarTag());
    }

    public Animal update(Long id, Animal animal) {
        if (animalMapper.findById(id) == null) return null;
        animal.setId(id);
        animalMapper.update(animal);
        return animalMapper.findById(id);
    }

    public boolean delete(Long id) {
        return animalMapper.deleteById(id) > 0;
    }

    public PageResult<Animal> findPage(String search, String status, int page, int size) {
        int offset = Math.max(0, page - 1) * size;
        String s = (search != null && !search.isBlank()) ? search : null;
        String st = (status != null && !status.isBlank()) ? status : null;
        List<Animal> content = animalMapper.findPage(s, st, offset, size);
        long total = animalMapper.countSearch(s, st);
        return new PageResult<>(content, total, page, size);
    }
}
