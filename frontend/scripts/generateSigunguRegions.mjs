/**
 * 시군구 SVG path 사전 계산 스크립트
 * 실행: node frontend/scripts/generateSigunguRegions.mjs
 *
 * 입력: frontend/src/assets/data/korea_sigungu.json (통계청 2018 GeoJSON)
 * 출력: frontend/src/assets/data/koreanSigunguRegions.js
 *
 * GeoJSON code(5자리) -> TourAPI (sidoCode, sigunguCode) 매핑은
 * 아래 SIGUNGU_CODE_MAP에 수동 정의.
 * 대도시 구(區) 단위 feature는 같은 TourAPI sigunguCode로 묶음.
 */

import { readFileSync, writeFileSync } from 'fs'
import { fileURLToPath } from 'url'
import { dirname, resolve } from 'path'
import * as topojson from 'topojson-client'
import { geoMercator, geoPath } from 'd3-geo'

const __dirname = dirname(fileURLToPath(import.meta.url))
const DATA_DIR  = resolve(__dirname, '../src/assets/data')

const SIGUNGU_CODE_MAP = {
  // 서울(1)
  '11010': { sidoCode: 1,  sigunguCode: 23 }, // 종로구
  '11020': { sidoCode: 1,  sigunguCode: 24 }, // 중구
  '11030': { sidoCode: 1,  sigunguCode: 21 }, // 용산구
  '11040': { sidoCode: 1,  sigunguCode: 16 }, // 성동구
  '11050': { sidoCode: 1,  sigunguCode: 6  }, // 광진구
  '11060': { sidoCode: 1,  sigunguCode: 11 }, // 동대문구
  '11070': { sidoCode: 1,  sigunguCode: 25 }, // 중랑구
  '11080': { sidoCode: 1,  sigunguCode: 17 }, // 성북구
  '11090': { sidoCode: 1,  sigunguCode: 3  }, // 강북구
  '11100': { sidoCode: 1,  sigunguCode: 10 }, // 도봉구
  '11110': { sidoCode: 1,  sigunguCode: 9  }, // 노원구
  '11120': { sidoCode: 1,  sigunguCode: 22 }, // 은평구
  '11130': { sidoCode: 1,  sigunguCode: 14 }, // 서대문구
  '11140': { sidoCode: 1,  sigunguCode: 13 }, // 마포구
  '11150': { sidoCode: 1,  sigunguCode: 19 }, // 양천구
  '11160': { sidoCode: 1,  sigunguCode: 4  }, // 강서구
  '11170': { sidoCode: 1,  sigunguCode: 7  }, // 구로구
  '11180': { sidoCode: 1,  sigunguCode: 8  }, // 금천구
  '11190': { sidoCode: 1,  sigunguCode: 20 }, // 영등포구
  '11200': { sidoCode: 1,  sigunguCode: 12 }, // 동작구
  '11210': { sidoCode: 1,  sigunguCode: 5  }, // 관악구
  '11220': { sidoCode: 1,  sigunguCode: 15 }, // 서초구
  '11230': { sidoCode: 1,  sigunguCode: 1  }, // 강남구
  '11240': { sidoCode: 1,  sigunguCode: 18 }, // 송파구
  '11250': { sidoCode: 1,  sigunguCode: 2  }, // 강동구
  // 부산(6)
  '21010': { sidoCode: 6,  sigunguCode: 15 }, // 중구
  '21020': { sidoCode: 6,  sigunguCode: 11 }, // 서구
  '21030': { sidoCode: 6,  sigunguCode: 5  }, // 동구
  '21040': { sidoCode: 6,  sigunguCode: 14 }, // 영도구
  '21050': { sidoCode: 6,  sigunguCode: 7  }, // 부산진구
  '21060': { sidoCode: 6,  sigunguCode: 6  }, // 동래구
  '21070': { sidoCode: 6,  sigunguCode: 4  }, // 남구
  '21080': { sidoCode: 6,  sigunguCode: 8  }, // 북구
  '21090': { sidoCode: 6,  sigunguCode: 16 }, // 해운대구
  '21100': { sidoCode: 6,  sigunguCode: 10 }, // 사하구
  '21110': { sidoCode: 6,  sigunguCode: 2  }, // 금정구
  '21120': { sidoCode: 6,  sigunguCode: 1  }, // 강서구
  '21130': { sidoCode: 6,  sigunguCode: 13 }, // 연제구
  '21140': { sidoCode: 6,  sigunguCode: 12 }, // 수영구
  '21150': { sidoCode: 6,  sigunguCode: 9  }, // 사상구
  '21310': { sidoCode: 6,  sigunguCode: 3  }, // 기장군
  // 대구(4)
  '22010': { sidoCode: 4,  sigunguCode: 8  }, // 중구
  '22020': { sidoCode: 4,  sigunguCode: 4  }, // 동구
  '22030': { sidoCode: 4,  sigunguCode: 6  }, // 서구
  '22040': { sidoCode: 4,  sigunguCode: 1  }, // 남구
  '22050': { sidoCode: 4,  sigunguCode: 5  }, // 북구
  '22060': { sidoCode: 4,  sigunguCode: 7  }, // 수성구
  '22070': { sidoCode: 4,  sigunguCode: 2  }, // 달서구
  '22310': { sidoCode: 4,  sigunguCode: 3  }, // 달성군
  '22320': { sidoCode: 4,  sigunguCode: 9  }, // 군위군
  // 인천(2)
  '23010': { sidoCode: 2,  sigunguCode: 10 }, // 중구
  '23020': { sidoCode: 2,  sigunguCode: 5  }, // 동구
  '23030': { sidoCode: 2,  sigunguCode: 3  }, // 미추홀구
  '23040': { sidoCode: 2,  sigunguCode: 8  }, // 연수구
  '23050': { sidoCode: 2,  sigunguCode: 4  }, // 남동구
  '23060': { sidoCode: 2,  sigunguCode: 6  }, // 부평구
  '23070': { sidoCode: 2,  sigunguCode: 2  }, // 계양구
  '23080': { sidoCode: 2,  sigunguCode: 7  }, // 서구
  '23310': { sidoCode: 2,  sigunguCode: 1  }, // 강화군
  '23320': { sidoCode: 2,  sigunguCode: 9  }, // 옹진군
  // 광주(5)
  '24010': { sidoCode: 5,  sigunguCode: 3  }, // 동구
  '24020': { sidoCode: 5,  sigunguCode: 5  }, // 서구
  '24030': { sidoCode: 5,  sigunguCode: 2  }, // 남구
  '24040': { sidoCode: 5,  sigunguCode: 4  }, // 북구
  '24050': { sidoCode: 5,  sigunguCode: 1  }, // 광산구
  // 대전(3)
  '25010': { sidoCode: 3,  sigunguCode: 2  }, // 동구
  '25020': { sidoCode: 3,  sigunguCode: 5  }, // 중구
  '25030': { sidoCode: 3,  sigunguCode: 3  }, // 서구
  '25040': { sidoCode: 3,  sigunguCode: 4  }, // 유성구
  '25050': { sidoCode: 3,  sigunguCode: 1  }, // 대덕구
  // 울산(7)
  '26010': { sidoCode: 7,  sigunguCode: 1  }, // 중구
  '26020': { sidoCode: 7,  sigunguCode: 2  }, // 남구
  '26030': { sidoCode: 7,  sigunguCode: 3  }, // 동구
  '26040': { sidoCode: 7,  sigunguCode: 4  }, // 북구
  '26310': { sidoCode: 7,  sigunguCode: 5  }, // 울주군
  // 세종(8)
  '29010': { sidoCode: 8,  sigunguCode: 1  }, // 세종시
  // 경기(31) — 수원/성남/안양/안산/고양/용인은 구 단위로 분리됨
  '31011': { sidoCode: 31, sigunguCode: 13 }, // 수원시 장안구
  '31012': { sidoCode: 31, sigunguCode: 13 }, // 수원시 권선구
  '31013': { sidoCode: 31, sigunguCode: 13 }, // 수원시 팔달구
  '31014': { sidoCode: 31, sigunguCode: 13 }, // 수원시 영통구
  '31021': { sidoCode: 31, sigunguCode: 12 }, // 성남시 수정구
  '31022': { sidoCode: 31, sigunguCode: 12 }, // 성남시 중원구
  '31023': { sidoCode: 31, sigunguCode: 12 }, // 성남시 분당구
  '31030': { sidoCode: 31, sigunguCode: 25 }, // 의정부시
  '31041': { sidoCode: 31, sigunguCode: 17 }, // 안양시 만안구
  '31042': { sidoCode: 31, sigunguCode: 17 }, // 안양시 동안구
  '31050': { sidoCode: 31, sigunguCode: 11 }, // 부천시
  '31060': { sidoCode: 31, sigunguCode: 4  }, // 광명시
  '31070': { sidoCode: 31, sigunguCode: 28 }, // 평택시
  '31080': { sidoCode: 31, sigunguCode: 10 }, // 동두천시
  '31091': { sidoCode: 31, sigunguCode: 15 }, // 안산시 상록구
  '31092': { sidoCode: 31, sigunguCode: 15 }, // 안산시 단원구
  '31101': { sidoCode: 31, sigunguCode: 2  }, // 고양시 덕양구
  '31103': { sidoCode: 31, sigunguCode: 2  }, // 고양시 일산동구
  '31104': { sidoCode: 31, sigunguCode: 2  }, // 고양시 일산서구
  '31110': { sidoCode: 31, sigunguCode: 3  }, // 과천시
  '31120': { sidoCode: 31, sigunguCode: 6  }, // 구리시
  '31130': { sidoCode: 31, sigunguCode: 9  }, // 남양주시
  '31140': { sidoCode: 31, sigunguCode: 22 }, // 오산시
  '31150': { sidoCode: 31, sigunguCode: 14 }, // 시흥시
  '31160': { sidoCode: 31, sigunguCode: 7  }, // 군포시
  '31170': { sidoCode: 31, sigunguCode: 24 }, // 의왕시
  '31180': { sidoCode: 31, sigunguCode: 30 }, // 하남시
  '31191': { sidoCode: 31, sigunguCode: 23 }, // 용인시 처인구
  '31192': { sidoCode: 31, sigunguCode: 23 }, // 용인시 기흥구
  '31193': { sidoCode: 31, sigunguCode: 23 }, // 용인시 수지구
  '31200': { sidoCode: 31, sigunguCode: 27 }, // 파주시
  '31210': { sidoCode: 31, sigunguCode: 26 }, // 이천시
  '31220': { sidoCode: 31, sigunguCode: 16 }, // 안성시
  '31230': { sidoCode: 31, sigunguCode: 8  }, // 김포시
  '31240': { sidoCode: 31, sigunguCode: 31 }, // 화성시
  '31250': { sidoCode: 31, sigunguCode: 5  }, // 광주시
  '31260': { sidoCode: 31, sigunguCode: 18 }, // 양주시
  '31270': { sidoCode: 31, sigunguCode: 29 }, // 포천시
  '31280': { sidoCode: 31, sigunguCode: 20 }, // 여주시
  '31350': { sidoCode: 31, sigunguCode: 21 }, // 연천군
  '31370': { sidoCode: 31, sigunguCode: 1  }, // 가평군
  '31380': { sidoCode: 31, sigunguCode: 19 }, // 양평군
  // 강원(32)
  '32010': { sidoCode: 32, sigunguCode: 13 }, // 춘천시
  '32020': { sidoCode: 32, sigunguCode: 9  }, // 원주시
  '32030': { sidoCode: 32, sigunguCode: 1  }, // 강릉시
  '32040': { sidoCode: 32, sigunguCode: 3  }, // 동해시
  '32050': { sidoCode: 32, sigunguCode: 14 }, // 태백시
  '32060': { sidoCode: 32, sigunguCode: 5  }, // 속초시
  '32070': { sidoCode: 32, sigunguCode: 4  }, // 삼척시
  '32310': { sidoCode: 32, sigunguCode: 16 }, // 홍천군
  '32320': { sidoCode: 32, sigunguCode: 18 }, // 횡성군
  '32330': { sidoCode: 32, sigunguCode: 8  }, // 영월군
  '32340': { sidoCode: 32, sigunguCode: 15 }, // 평창군
  '32350': { sidoCode: 32, sigunguCode: 11 }, // 정선군
  '32360': { sidoCode: 32, sigunguCode: 12 }, // 철원군 (GeoJSON name_eng 오류 — 실제 철원군)
  '32370': { sidoCode: 32, sigunguCode: 17 }, // 화천군
  '32380': { sidoCode: 32, sigunguCode: 6  }, // 양구군
  '32390': { sidoCode: 32, sigunguCode: 10 }, // 인제군
  '32400': { sidoCode: 32, sigunguCode: 2  }, // 고성군
  '32410': { sidoCode: 32, sigunguCode: 7  }, // 양양군
  // 충북(33) — 청주시는 구 단위로 분리됨
  '33020': { sidoCode: 33, sigunguCode: 11 }, // 충주시
  '33030': { sidoCode: 33, sigunguCode: 7  }, // 제천시
  '33041': { sidoCode: 33, sigunguCode: 10 }, // 청주시 상당구
  '33042': { sidoCode: 33, sigunguCode: 10 }, // 청주시 서원구
  '33043': { sidoCode: 33, sigunguCode: 10 }, // 청주시 흥덕구
  '33044': { sidoCode: 33, sigunguCode: 10 }, // 청주시 청원구
  '33320': { sidoCode: 33, sigunguCode: 3  }, // 보은군
  '33330': { sidoCode: 33, sigunguCode: 5  }, // 옥천군
  '33340': { sidoCode: 33, sigunguCode: 4  }, // 영동군
  '33350': { sidoCode: 33, sigunguCode: 8  }, // 진천군
  '33360': { sidoCode: 33, sigunguCode: 1  }, // 괴산군
  '33370': { sidoCode: 33, sigunguCode: 6  }, // 음성군
  '33380': { sidoCode: 33, sigunguCode: 2  }, // 단양군
  '33390': { sidoCode: 33, sigunguCode: 12 }, // 증평군
  // 충남(34) — 천안시는 구 단위로 분리됨
  '34011': { sidoCode: 34, sigunguCode: 12 }, // 천안시 동남구
  '34012': { sidoCode: 34, sigunguCode: 12 }, // 천안시 서북구
  '34020': { sidoCode: 34, sigunguCode: 1  }, // 공주시
  '34030': { sidoCode: 34, sigunguCode: 5  }, // 보령시
  '34040': { sidoCode: 34, sigunguCode: 9  }, // 아산시
  '34050': { sidoCode: 34, sigunguCode: 7  }, // 서산시
  '34060': { sidoCode: 34, sigunguCode: 3  }, // 논산시
  '34070': { sidoCode: 34, sigunguCode: 16 }, // 계룡시
  '34080': { sidoCode: 34, sigunguCode: 4  }, // 당진시
  '34310': { sidoCode: 34, sigunguCode: 2  }, // 금산군
  '34330': { sidoCode: 34, sigunguCode: 6  }, // 부여군
  '34340': { sidoCode: 34, sigunguCode: 8  }, // 서천군
  '34350': { sidoCode: 34, sigunguCode: 13 }, // 청양군
  '34360': { sidoCode: 34, sigunguCode: 15 }, // 홍성군
  '34370': { sidoCode: 34, sigunguCode: 11 }, // 예산군
  '34380': { sidoCode: 34, sigunguCode: 14 }, // 태안군
  // 전북(37) — GeoJSON은 35xxx 사용, 전주시는 구 단위로 분리됨
  '35011': { sidoCode: 37, sigunguCode: 12 }, // 전주시 완산구
  '35012': { sidoCode: 37, sigunguCode: 12 }, // 전주시 덕진구
  '35020': { sidoCode: 37, sigunguCode: 2  }, // 군산시
  '35030': { sidoCode: 37, sigunguCode: 9  }, // 익산시
  '35040': { sidoCode: 37, sigunguCode: 13 }, // 정읍시
  '35050': { sidoCode: 37, sigunguCode: 4  }, // 남원시
  '35060': { sidoCode: 37, sigunguCode: 3  }, // 김제시
  '35310': { sidoCode: 37, sigunguCode: 8  }, // 완주군
  '35320': { sidoCode: 37, sigunguCode: 14 }, // 진안군
  '35330': { sidoCode: 37, sigunguCode: 5  }, // 무주군
  '35340': { sidoCode: 37, sigunguCode: 11 }, // 장수군
  '35350': { sidoCode: 37, sigunguCode: 10 }, // 임실군
  '35360': { sidoCode: 37, sigunguCode: 7  }, // 순창군
  '35370': { sidoCode: 37, sigunguCode: 1  }, // 고창군
  '35380': { sidoCode: 37, sigunguCode: 6  }, // 부안군
  // 전남(38) — GeoJSON은 36xxx 사용
  '36010': { sidoCode: 38, sigunguCode: 8  }, // 목포시
  '36020': { sidoCode: 38, sigunguCode: 13 }, // 여수시
  '36030': { sidoCode: 38, sigunguCode: 11 }, // 순천시
  '36040': { sidoCode: 38, sigunguCode: 6  }, // 나주시
  '36060': { sidoCode: 38, sigunguCode: 4  }, // 광양시
  '36310': { sidoCode: 38, sigunguCode: 7  }, // 담양군
  '36320': { sidoCode: 38, sigunguCode: 3  }, // 곡성군
  '36330': { sidoCode: 38, sigunguCode: 5  }, // 구례군
  '36350': { sidoCode: 38, sigunguCode: 2  }, // 고흥군
  '36360': { sidoCode: 38, sigunguCode: 10 }, // 보성군
  '36370': { sidoCode: 38, sigunguCode: 24 }, // 화순군
  '36380': { sidoCode: 38, sigunguCode: 20 }, // 장흥군
  '36390': { sidoCode: 38, sigunguCode: 1  }, // 강진군
  '36400': { sidoCode: 38, sigunguCode: 23 }, // 해남군
  '36410': { sidoCode: 38, sigunguCode: 17 }, // 영암군
  '36420': { sidoCode: 38, sigunguCode: 9  }, // 무안군
  '36430': { sidoCode: 38, sigunguCode: 22 }, // 함평군
  '36440': { sidoCode: 38, sigunguCode: 16 }, // 영광군
  '36450': { sidoCode: 38, sigunguCode: 19 }, // 장성군
  '36460': { sidoCode: 38, sigunguCode: 18 }, // 완도군
  '36470': { sidoCode: 38, sigunguCode: 21 }, // 진도군
  '36480': { sidoCode: 38, sigunguCode: 12 }, // 신안군
  // 경북(35) — GeoJSON은 37xxx 사용, 포항시는 구 단위로 분리됨
  '37011': { sidoCode: 35, sigunguCode: 23 }, // 포항시 남구
  '37012': { sidoCode: 35, sigunguCode: 23 }, // 포항시 북구
  '37020': { sidoCode: 35, sigunguCode: 2  }, // 경주시
  '37030': { sidoCode: 35, sigunguCode: 6  }, // 김천시
  '37040': { sidoCode: 35, sigunguCode: 11 }, // 안동시
  '37050': { sidoCode: 35, sigunguCode: 4  }, // 구미시
  '37060': { sidoCode: 35, sigunguCode: 14 }, // 영주시
  '37070': { sidoCode: 35, sigunguCode: 15 }, // 영천시
  '37080': { sidoCode: 35, sigunguCode: 9  }, // 상주시
  '37090': { sidoCode: 35, sigunguCode: 7  }, // 문경시
  '37100': { sidoCode: 35, sigunguCode: 1  }, // 경산시
  '37310': { sidoCode: 4,  sigunguCode: 9  }, // 군위군 (2023 대구 편입, TourAPI 4/9)
  '37320': { sidoCode: 35, sigunguCode: 19 }, // 의성군
  '37330': { sidoCode: 35, sigunguCode: 21 }, // 청송군
  '37340': { sidoCode: 35, sigunguCode: 13 }, // 영양군
  '37350': { sidoCode: 35, sigunguCode: 12 }, // 영덕군
  '37360': { sidoCode: 35, sigunguCode: 20 }, // 청도군
  '37370': { sidoCode: 35, sigunguCode: 3  }, // 고령군
  '37380': { sidoCode: 35, sigunguCode: 10 }, // 성주군
  '37390': { sidoCode: 35, sigunguCode: 22 }, // 칠곡군
  '37400': { sidoCode: 35, sigunguCode: 16 }, // 예천군
  '37410': { sidoCode: 35, sigunguCode: 8  }, // 봉화군
  '37420': { sidoCode: 35, sigunguCode: 18 }, // 울진군
  '37430': { sidoCode: 35, sigunguCode: 17 }, // 울릉군
  // 경남(36) — GeoJSON은 38xxx 사용, 창원시는 구 단위로 분리됨
  '38030': { sidoCode: 36, sigunguCode: 13 }, // 진주시
  '38050': { sidoCode: 36, sigunguCode: 17 }, // 통영시
  '38060': { sidoCode: 36, sigunguCode: 8  }, // 사천시
  '38070': { sidoCode: 36, sigunguCode: 4  }, // 김해시
  '38080': { sidoCode: 36, sigunguCode: 7  }, // 밀양시
  '38090': { sidoCode: 36, sigunguCode: 1  }, // 거제시
  '38100': { sidoCode: 36, sigunguCode: 10 }, // 양산시
  '38111': { sidoCode: 36, sigunguCode: 16 }, // 창원시 의창구
  '38112': { sidoCode: 36, sigunguCode: 16 }, // 창원시 성산구
  '38113': { sidoCode: 36, sigunguCode: 16 }, // 창원시 마산합포구
  '38114': { sidoCode: 36, sigunguCode: 16 }, // 창원시 마산회원구
  '38115': { sidoCode: 36, sigunguCode: 16 }, // 창원시 진해구
  '38310': { sidoCode: 36, sigunguCode: 12 }, // 의령군
  '38320': { sidoCode: 36, sigunguCode: 19 }, // 함안군
  '38330': { sidoCode: 36, sigunguCode: 15 }, // 창녕군
  '38340': { sidoCode: 36, sigunguCode: 3  }, // 고성군
  '38350': { sidoCode: 36, sigunguCode: 5  }, // 남해군
  '38360': { sidoCode: 36, sigunguCode: 18 }, // 하동군
  '38370': { sidoCode: 36, sigunguCode: 9  }, // 산청군
  '38380': { sidoCode: 36, sigunguCode: 20 }, // 함양군
  '38390': { sidoCode: 36, sigunguCode: 2  }, // 거창군
  '38400': { sidoCode: 36, sigunguCode: 21 }, // 합천군
  // 제주(39)
  '39010': { sidoCode: 39, sigunguCode: 4  }, // 제주시
  '39020': { sidoCode: 39, sigunguCode: 3  }, // 서귀포시
}

const geoJson = JSON.parse(readFileSync(resolve(DATA_DIR, 'korea_sigungu.json'), 'utf-8'))

// sido 경계 기준으로 투영 설정 (koreaRegions.js와 동일한 viewBox 500x620)
const sidoGeoJson = JSON.parse(readFileSync(resolve(DATA_DIR, 'korea_sido.json'), 'utf-8'))
const sidoFeatures = topojson.feature(sidoGeoJson, sidoGeoJson.objects[Object.keys(sidoGeoJson.objects)[0]])

const proj = geoMercator().fitSize([500, 620], sidoFeatures)
const pathGen = geoPath().projection(proj)

const regions = []
let skipped = 0

for (const feature of geoJson.features) {
  const geoCode = feature.properties.code
  const name    = feature.properties.name
  const mapping = SIGUNGU_CODE_MAP[geoCode]

  if (!mapping) {
    console.warn(`[SKIP] code=${geoCode} name_eng=${feature.properties.name_eng}`)
    skipped++
    continue
  }

  const svgPath  = pathGen(feature)
  const centroid = pathGen.centroid(feature)

  regions.push({
    sidoCode:    mapping.sidoCode,
    sigunguCode: mapping.sigunguCode,
    name,
    svgPath,
    cx: centroid[0] ? Math.round(centroid[0] * 10) / 10 : null,
    cy: centroid[1] ? Math.round(centroid[1] * 10) / 10 : null,
  })
}

regions.sort((a, b) => a.sidoCode - b.sidoCode || a.sigunguCode - b.sigunguCode)

const output = `// 사전 계산된 한국 시군구 SVG path (viewBox 500x620, sido와 동일 투영)
// Node.js + d3-geo + 통계청 2018 GeoJSON으로 생성
// 총 ${regions.length}개 시군구 폴리곤 (대도시 구 단위 포함)
export const KOREA_SIGUNGU_REGIONS = ${JSON.stringify(regions, null, 2)}
`

writeFileSync(resolve(DATA_DIR, 'koreanSigunguRegions.js'), output, 'utf-8')
console.log(`완료: ${regions.length}개 폴리곤 생성 (스킵: ${skipped}개)`)
