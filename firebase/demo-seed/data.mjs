// 데모 데이터 정의와 좌표 계산. 모양은 지역 평면 좌표(m)로 정의하고 기준점에서 위경도로 바꾼다.
// 실제 도로에 맞춘 경로가 아니라 서울 지명 근처에 그린 모양이다.

export const USERS = [
  { key: 'gangnam', nickname: '강남드리프터' },
  { key: 'yeouido', nickname: '여의도산책러' },
  { key: 'cityhall', nickname: '시청앞단골' },
  { key: 'jamsil', nickname: '잠실한바퀴' },
  { key: 'hangang', nickname: '한강라이더' },
  { key: 'mapo', nickname: '마포드라이브' },
];

const METERS_PER_LAT = 110540;
const metersPerLon = (lat) => 111320 * Math.cos((lat * Math.PI) / 180);

export function toLatLon(origin, [x, y]) {
  return { latitude: origin.lat + y / METERS_PER_LAT, longitude: origin.lon + x / metersPerLon(origin.lat) };
}

function toLocal(origin, lat, lon) {
  return [(lon - origin.lon) * metersPerLon(origin.lat), (lat - origin.lat) * METERS_PER_LAT];
}

// 실행할 때마다 같은 경로가 나오도록 고정 시드 난수를 쓴다.
function random(seed) {
  let value = seed >>> 0;
  return () => {
    value = (value * 1664525 + 1013904223) >>> 0;
    return value / 2 ** 32;
  };
}

/** 꼭짓점 목록을 [spacing]m 간격으로 다시 나누고 GPS처럼 약간 흔든다. */
function resample(vertices, spacing, seed) {
  const next = random(seed);
  const points = [];
  for (let i = 0; i < vertices.length - 1; i++) {
    const [x0, y0] = vertices[i];
    const [x1, y1] = vertices[i + 1];
    const steps = Math.max(1, Math.round(Math.hypot(x1 - x0, y1 - y0) / spacing));
    for (let s = 0; s < steps; s++) {
      const t = s / steps;
      points.push([x0 + (x1 - x0) * t + (next() - 0.5) * 4, y0 + (y1 - y0) * t + (next() - 0.5) * 4]);
    }
  }
  points.push(vertices[vertices.length - 1]);
  return points;
}

/** 지역 평면 경로를 앱의 경로 점(위도, 경도, 기록 시각)으로 바꾼다. 초속 [speed]m로 달린 것처럼 시각을 매긴다. */
export function routeOf(origin, vertices, { startMillis, seed, spacing = 12, speed = 9 }) {
  const local = resample(vertices, spacing, seed);
  let time = startMillis;
  let distance = 0;
  return {
    points: local.map((point, index) => {
      if (index > 0) {
        const step = Math.hypot(point[0] - local[index - 1][0], point[1] - local[index - 1][1]);
        distance += step;
        time += Math.round((step / speed) * 1000);
      }
      return { ...toLatLon(origin, point), recordedAtMillis: time };
    }),
    distanceMeters: Math.round(distance * 10) / 10,
    durationMillis: time - startMillis,
  };
}

function parametric(count, fn) {
  return Array.from({ length: count + 1 }, (_, i) => fn((i / count) * 2 * Math.PI));
}

function ellipse(a, b, rotation = 0, count = 72) {
  const c = Math.cos(rotation);
  const s = Math.sin(rotation);
  return parametric(count, (t) => [a * Math.cos(t) * c - b * Math.sin(t) * s, a * Math.cos(t) * s + b * Math.sin(t) * c]);
}

const SHAPES = {
  heart: parametric(120, (t) => [
    40 * 16 * Math.sin(t) ** 3,
    40 * (13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t)),
  ]),
  star: Array.from({ length: 11 }, (_, i) => {
    const radius = i % 2 === 0 ? 700 : 280;
    const angle = Math.PI / 2 + (i * Math.PI) / 5;
    return [radius * Math.cos(angle), radius * Math.sin(angle)];
  }),
  hi: [[-400, -400], [-400, 400], [-400, 0], [0, 0], [0, 400], [0, -400], [250, -400], [250, 400], [150, 400], [350, 400]],
  spiral: Array.from({ length: 181 }, (_, i) => {
    const theta = (i / 180) * 6 * Math.PI;
    const radius = 60 + 35 * theta;
    return [radius * Math.cos(theta), radius * Math.sin(theta)];
  }),
  fish: [
    ...parametric(60, (t) => [500 * Math.cos(t + Math.PI), 250 * Math.sin(t + Math.PI)]),
    [-800, 250], [-800, -250], [-500, 0],
  ],
  crown: [[-450, -300], [450, -300], [450, 300], [225, 0], [0, 400], [-225, 0], [-450, 300], [-450, -300]],
  lightning: [[0, 450], [-300, -100], [-50, -100], [-200, -550], [200, 50], [-50, 50], [150, 450], [0, 450]],
};

const DAY = 24 * 60 * 60 * 1000;
// 2026-11-01 09:00 KST. 다시 실행해도 같은 시각이 나오도록 고정한다.
const BASE_MILLIS = Date.UTC(2026, 10, 1, 0, 0, 0);

/** 공개 자유그림 작품. ID는 고정해 다시 실행해도 같은 문서를 쓴다. 제목은 20자 이하. */
export const ARTWORKS = [
  { id: 'demo-artwork-heart', owner: 'yeouido', title: '여의도 하트', shape: 'heart', origin: { lat: 37.5263, lon: 126.922 }, day: 0, voters: ['gangnam', 'cityhall', 'jamsil', 'hangang', 'mapo'] },
  { id: 'demo-artwork-star', owner: 'gangnam', title: '강남에 뜬 별', shape: 'star', origin: { lat: 37.5, lon: 127.03 }, day: 1, voters: ['yeouido', 'cityhall', 'jamsil', 'mapo'] },
  { id: 'demo-artwork-hi', owner: 'cityhall', title: 'HI 서울', shape: 'hi', origin: { lat: 37.566, lon: 126.978 }, day: 2, voters: ['gangnam', 'yeouido', 'hangang'] },
  { id: 'demo-artwork-crown', owner: 'hangang', title: '광화문 왕관', shape: 'crown', origin: { lat: 37.576, lon: 126.977 }, day: 3, voters: ['cityhall', 'jamsil', 'mapo'] },
  { id: 'demo-artwork-fish', owner: 'mapo', title: '상암 물고기', shape: 'fish', origin: { lat: 37.568, lon: 126.897 }, day: 4, voters: ['hangang', 'yeouido'] },
  { id: 'demo-artwork-spiral', owner: 'jamsil', title: '잠실 소용돌이', shape: 'spiral', origin: { lat: 37.511, lon: 127.098 }, day: 5, voters: ['gangnam'] },
  { id: 'demo-artwork-lightning', owner: 'gangnam', title: '성수 번개', shape: 'lightning', origin: { lat: 37.5445, lon: 127.056 }, day: 6, voters: ['mapo'] },
];

export function artworkRoute(artwork, seed) {
  return routeOf(artwork.origin, SHAPES[artwork.shape], { startMillis: BASE_MILLIS + artwork.day * DAY, seed });
}

/**
 * 땅따먹기 획득. 순서대로 제출한다. 서울시청 주변(영토 지도를 처음 여는 곳)에는 데모 사용자마다 동네 하나씩을
 * 서로 떨어진 덩어리로 둔다. 앱은 다른 사용자의 칸을 모두 같은 색으로 그리므로 덩어리가 맞닿으면 하나로 보인다.
 * 그 밖에 사용자마다 자기 동네 영토가 있다. 획득 ID는 고정이라 다시 실행하면 이미 반영된 획득으로 건너뛴다.
 */
const CITY_HALL = { lat: 37.5665, lon: 126.978 };

/** 서울시청에서 동쪽 [x]m, 북쪽 [y]m 떨어진 기준점 */
function nearCityHall(x, y) {
  const point = toLatLon(CITY_HALL, [x, y]);
  return { lat: point.latitude, lon: point.longitude };
}

export const CAPTURES = [
  { id: 'demo-capture-gwanghwamun', owner: 'hangang', origin: nearCityHall(-320, 620), polygon: ellipse(390, 290) },
  { id: 'demo-capture-jongno', owner: 'jamsil', origin: nearCityHall(640, 600), polygon: ellipse(400, 280, 0.15) },
  { id: 'demo-capture-cityhall', owner: 'cityhall', origin: nearCityHall(-360, -90), polygon: ellipse(340, 280) },
  { id: 'demo-capture-myeongdong', owner: 'gangnam', origin: nearCityHall(660, -250), polygon: ellipse(380, 300, -0.2) },
  { id: 'demo-capture-sungnyemun', owner: 'mapo', origin: nearCityHall(-260, -760), polygon: ellipse(360, 200) },
  { id: 'demo-capture-seodaemun', owner: 'yeouido', origin: nearCityHall(-1050, 280), polygon: ellipse(230, 330) },
  { id: 'demo-capture-gangnam', owner: 'gangnam', origin: { lat: 37.4979, lon: 127.0276 }, polygon: [[-900, -500], [300, -800], [1000, -200], [800, 700], [-200, 800], [-1000, 200], [-900, -500]] },
  { id: 'demo-capture-yeouido', owner: 'yeouido', origin: { lat: 37.525, lon: 126.926 }, polygon: ellipse(1300, 550, Math.PI / 6) },
  { id: 'demo-capture-jamsil', owner: 'jamsil', origin: { lat: 37.513, lon: 127.1 }, polygon: ellipse(650, 650) },
  { id: 'demo-capture-mapo', owner: 'mapo', origin: { lat: 37.5563, lon: 126.9236 }, polygon: [[-450, -300], [400, -350], [450, 300], [-350, 350], [-450, -300]] },
  { id: 'demo-capture-banpo', owner: 'hangang', origin: { lat: 37.5115, lon: 126.996 }, polygon: ellipse(1100, 220, -Math.PI / 12) },
];

// 앱의 TerritoryGrid와 같은 정사각형 격자(서울 기준 약 120m x 120m). 칸 ID는 "{row}_{col}", 구역은 두 번호를 8로 나눈 몫이다.
const LAT_STEP = 0.00108;
const LON_STEP = 0.00136;
const REGION_CELLS = 8;

export function regionOf(cellId) {
  const [row, col] = cellId.split('_').map(Number);
  return `${Math.floor(row / REGION_CELLS)}_${Math.floor(col / REGION_CELLS)}`;
}

function inside(polygon, [x, y]) {
  let result = false;
  for (let i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
    const [xi, yi] = polygon[i];
    const [xj, yj] = polygon[j];
    if (yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) result = !result;
  }
  return result;
}

/** 앱의 territoryCellsOf처럼 다각형 안에 중심이 들어오는 격자 칸을 고른다. */
export function captureCells(capture) {
  const corners = capture.polygon.map((p) => toLatLon(capture.origin, p));
  const minRow = Math.floor((Math.min(...corners.map((c) => c.latitude)) + 90) / LAT_STEP);
  const maxRow = Math.floor((Math.max(...corners.map((c) => c.latitude)) + 90) / LAT_STEP);
  const minCol = Math.floor((Math.min(...corners.map((c) => c.longitude)) + 180) / LON_STEP);
  const maxCol = Math.floor((Math.max(...corners.map((c) => c.longitude)) + 180) / LON_STEP);
  const cells = [];
  for (let row = minRow; row <= maxRow; row++) {
    for (let col = minCol; col <= maxCol; col++) {
      const lat = -90 + (row + 0.5) * LAT_STEP;
      const lon = -180 + (col + 0.5) * LON_STEP;
      if (inside(capture.polygon, toLocal(capture.origin, lat, lon))) cells.push(`${row}_${col}`);
    }
  }
  return cells.sort();
}

/** 동그라미 챌린지 기록. 점수가 낮을수록 찌그러진 원으로 그린다. */
export const CIRCLES = [
  { owner: 'gangnam', score: 96, origin: { lat: 37.5172, lon: 127.0473 }, day: 2 },
  { owner: 'cityhall', score: 91, origin: { lat: 37.5651, lon: 126.9895 }, day: 3 },
  { owner: 'yeouido', score: 87, origin: { lat: 37.5219, lon: 126.9245 }, day: 4 },
  { owner: 'hangang', score: 79, origin: { lat: 37.5283, lon: 126.9341 }, day: 5 },
  { owner: 'jamsil', score: 72, origin: { lat: 37.5145, lon: 127.1059 }, day: 6 },
  { owner: 'mapo', score: 64, origin: { lat: 37.5547, lon: 126.9106 }, day: 7 },
].map((circle) => ({ ...circle, drawingId: `demo-circle-${circle.owner}` }));

export function circleRoute(circle, seed) {
  const squish = 1 + ((100 - circle.score) / 100) * 1.2;
  const shape = parametric(72, (t) => [250 * squish * Math.cos(t), (250 / squish) * Math.sin(t) + 20 * Math.sin(3 * t) * (squish - 1)]);
  return routeOf(circle.origin, shape, { startMillis: BASE_MILLIS + circle.day * DAY + 3 * 60 * 60 * 1000, seed, speed: 7 });
}
