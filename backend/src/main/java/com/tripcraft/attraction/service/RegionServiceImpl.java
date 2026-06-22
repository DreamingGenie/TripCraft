package com.tripcraft.attraction.service;

import com.tripcraft.attraction.domain.Sido;
import com.tripcraft.attraction.domain.Sigungu;
import com.tripcraft.attraction.dto.RegionWithSigunguDto;
import com.tripcraft.attraction.dto.SigunguItem;
import com.tripcraft.attraction.mapper.SidoMapper;
import com.tripcraft.attraction.mapper.SigunguMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RegionServiceImpl implements RegionService {

    private final SidoMapper sidoMapper;
    private final SigunguMapper sigunguMapper;

    /** alias 우선, 없으면 공식명 */
    private static String label(String alias, String name) {
        return (alias != null && !alias.isBlank()) ? alias : name;
    }

    @Override
    public Map<Integer, String> sidoLabelMap() {
        Map<Integer, String> m = new LinkedHashMap<>();
        for (Sido s : sidoMapper.findAll()) {
            m.put(s.getSidoCode(), label(s.getAlias(), s.getName()));
        }
        return m;
    }

    @Override
    public Map<Integer, Map<Integer, String>> sigunguLabelMap() {
        Map<Integer, Map<Integer, String>> m = new LinkedHashMap<>();
        for (Sigungu sg : sigunguMapper.findAll()) {
            m.computeIfAbsent(sg.getSidoCode(), k -> new LinkedHashMap<>())
             .put(sg.getSigunguCode(), label(sg.getAlias(), sg.getName()));
        }
        return m;
    }

    @Override
    public Map<String, Integer> sidoCodeByLabel() {
        Map<String, Integer> m = new HashMap<>();
        for (Sido s : sidoMapper.findAll()) {
            if (s.getName() != null) m.put(s.getName(), s.getSidoCode());
            if (s.getAlias() != null && !s.getAlias().isBlank()) m.put(s.getAlias(), s.getSidoCode());
        }
        return m;
    }

    @Override
    public List<RegionWithSigunguDto> getRegionsWithSigungu() {
        Map<Integer, Map<Integer, String>> sgMaps = sigunguLabelMap();
        List<RegionWithSigunguDto> result = new ArrayList<>();
        for (Sido s : sidoMapper.findAll()) {
            Map<Integer, String> sgMap = sgMaps.getOrDefault(s.getSidoCode(), Map.of());
            List<SigunguItem> sgList = sgMap.entrySet().stream()
                .map(e -> new SigunguItem(s.getSidoCode(), e.getKey(), e.getValue()))
                .collect(Collectors.toList());
            result.add(new RegionWithSigunguDto(label(s.getAlias(), s.getName()), s.getSidoCode(), sgList));
        }
        result.sort((a, b) -> Integer.compare(a.sidoCode(), b.sidoCode()));
        return result;
    }
}
