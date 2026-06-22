/**
 * 시도 SVG path 사전 계산 스크립트
 * 실행: node frontend/scripts/generateSidoRegions.mjs
 *
 * 입력: frontend/src/assets/data/korea_sido.json (Highcharts TopoJSON)
 * 출력: frontend/src/assets/data/koreaRegions.js
 */

import { readFileSync, writeFileSync } from 'fs'
import { fileURLToPath } from 'url'
import { dirname, resolve } from 'path'
import * as topojson from 'topojson-client'
import { geoMercator, geoPath, geoCentroid } from 'd3-geo'

const __dirname = dirname(fileURLToPath(import.meta.url))
const DATA_DIR  = resolve(__dirname, '../src/assets/data')

// Highcharts TopoJSON id → TourAPI sidoCode + 한글명
const SIDO_CODE_MAP = {
  'KR.SO':   { code: 1,  name: '서울' },
  'KR.IN':   { code: 2,  name: '인천' },
  'KR.TJ':   { code: 3,  name: '대전' },
  'KR.TG':   { code: 4,  name: '대구' },
  'KR.KJ':   { code: 5,  name: '광주' },
  'KR.PU':   { code: 6,  name: '부산' },
  'KR.UL':   { code: 7,  name: '울산' },
  'KR.SJ':   { code: 8,  name: '세종' },
  'KR.KG':   { code: 31, name: '경기' },
  'KR.KW':   { code: 32, name: '강원' },
  'KR.GB':   { code: 33, name: '충북' },
  'KR.GN':   { code: 34, name: '충남' },
  'KR.CB':   { code: 35, name: '전북' },
  'KR.2685': { code: 36, name: '전남' },
  'KR.2688': { code: 37, name: '경북' },
  'KR.KN':   { code: 38, name: '경남' },
  'KR.CJ':   { code: 39, name: '제주' },
}

const topoRaw  = JSON.parse(readFileSync(resolve(DATA_DIR, 'korea_sido.json'), 'utf-8'))
const geoData  = topojson.feature(topoRaw, topoRaw.objects.default)

// 전체 지형에 맞춰 [300, 620] 공간에 투영
const proj     = geoMercator().fitSize([300, 620], geoData)
const pathGen  = geoPath().projection(proj)

const results = []
let skipped   = 0

for (const feature of geoData.features) {
  const id    = feature.id
  const entry = SIDO_CODE_MAP[id]
  if (!entry) {
    console.warn(`SKIP: id="${id}"`)
    skipped++
    continue
  }

  const svgPath = pathGen(feature)
  const [cx, cy] = proj(geoCentroid(feature))

  results.push({
    code:    entry.code,
    name:    entry.name,
    svgPath,
    cx:      Math.round(cx * 10) / 10,
    cy:      Math.round(cy * 10) / 10,
  })
}

results.sort((a, b) => a.code - b.code)

const js = `// 사전 계산된 한국 시도 SVG path (viewBox 300x620)
// Node.js + d3-geo + topojson-client로 생성 (Highcharts TopoJSON 기반)
export const KOREA_REGIONS = ${JSON.stringify(results, null, 2)}
`

writeFileSync(resolve(DATA_DIR, 'koreaRegions.js'), js, 'utf-8')
console.log(`완료: ${results.length}개 시도 생성, ${skipped}개 스킵`)
