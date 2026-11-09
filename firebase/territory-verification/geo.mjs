// geohash 칸 계산과 평면 근사 면적 계산. 검증용으로 서울 부근의 작은 범위만 다룬다.
const BASE32 = '0123456789bcdefghjkmnpqrstuvwxyz';

export const ORIGIN = { lat: 37.5665, lon: 126.978 };

export function encode(lat, lon, precision) {
  const latRange = [-90, 90];
  const lonRange = [-180, 180];
  let hash = '';
  let bits = 0;
  let value = 0;
  let evenBit = true;
  while (hash.length < precision) {
    const range = evenBit ? lonRange : latRange;
    const coordinate = evenBit ? lon : lat;
    const mid = (range[0] + range[1]) / 2;
    if (coordinate >= mid) {
      value = (value << 1) | 1;
      range[0] = mid;
    } else {
      value <<= 1;
      range[1] = mid;
    }
    evenBit = !evenBit;
    if (++bits === 5) {
      hash += BASE32[value];
      bits = 0;
      value = 0;
    }
  }
  return hash;
}

export function bounds(hash) {
  const latRange = [-90, 90];
  const lonRange = [-180, 180];
  let evenBit = true;
  for (const char of hash) {
    const value = BASE32.indexOf(char);
    for (let bit = 4; bit >= 0; bit--) {
      const range = evenBit ? lonRange : latRange;
      const mid = (range[0] + range[1]) / 2;
      if ((value >> bit) & 1) range[0] = mid;
      else range[1] = mid;
      evenBit = !evenBit;
    }
  }
  return { minLat: latRange[0], maxLat: latRange[1], minLon: lonRange[0], maxLon: lonRange[1] };
}

export function cellSize(precision, at = ORIGIN) {
  const b = bounds(encode(at.lat, at.lon, precision));
  return { dLat: b.maxLat - b.minLat, dLon: b.maxLon - b.minLon };
}

export function cellSizeMeters(precision, at = ORIGIN) {
  const { dLat, dLon } = cellSize(precision, at);
  return { height: dLat * 110540, width: dLon * 111320 * Math.cos((at.lat * Math.PI) / 180) };
}

// 기준 칸에서 (row, col)만큼 떨어진 칸들로 이루어진 직사각형 블록.
export function block(row, col, rows, cols, precision = 7) {
  const { dLat, dLon } = cellSize(precision);
  const base = bounds(encode(ORIGIN.lat, ORIGIN.lon, precision));
  const centerLat = (base.minLat + base.maxLat) / 2;
  const centerLon = (base.minLon + base.maxLon) / 2;
  const cells = [];
  for (let r = row; r < row + rows; r++) {
    for (let c = col; c < col + cols; c++) {
      cells.push(encode(centerLat + r * dLat, centerLon + c * dLon, precision));
    }
  }
  return cells;
}

export function coverBox(box, precision) {
  const { dLat, dLon } = cellSize(precision, { lat: box.minLat, lon: box.minLon });
  const hashes = new Set();
  for (let lat = box.minLat; lat < box.maxLat + dLat; lat += dLat) {
    for (let lon = box.minLon; lon < box.maxLon + dLon; lon += dLon) {
      hashes.add(encode(Math.min(lat, box.maxLat), Math.min(lon, box.maxLon), precision));
    }
  }
  return [...hashes];
}

export function boxAround(centerMetersX, centerMetersY, halfWidth, halfHeight) {
  return {
    minLat: toLat(centerMetersY - halfHeight),
    maxLat: toLat(centerMetersY + halfHeight),
    minLon: toLon(centerMetersX - halfWidth),
    maxLon: toLon(centerMetersX + halfWidth),
  };
}

function metersPerDegreeLon() {
  return 111320 * Math.cos((ORIGIN.lat * Math.PI) / 180);
}

export const toLat = (y) => ORIGIN.lat + y / 110540;
export const toLon = (x) => ORIGIN.lon + x / metersPerDegreeLon();

// 원점 기준 미터 좌표의 직사각형을 [lon, lat] 고리 하나짜리 다각형으로 만든다.
export function rectPolygon(x, y, width, height) {
  const ring = [
    [toLon(x), toLat(y)],
    [toLon(x + width), toLat(y)],
    [toLon(x + width), toLat(y + height)],
    [toLon(x), toLat(y + height)],
    [toLon(x), toLat(y)],
  ];
  return [ring];
}

function ringArea(ring) {
  let sum = 0;
  for (let i = 0; i < ring.length - 1; i++) {
    const x1 = (ring[i][0] - ORIGIN.lon) * metersPerDegreeLon();
    const y1 = (ring[i][1] - ORIGIN.lat) * 110540;
    const x2 = (ring[i + 1][0] - ORIGIN.lon) * metersPerDegreeLon();
    const y2 = (ring[i + 1][1] - ORIGIN.lat) * 110540;
    sum += x1 * y2 - x2 * y1;
  }
  return Math.abs(sum) / 2;
}

// polygon-clipping MultiPolygon: [polygon[ring[[lon, lat]]]], 첫 고리는 외곽, 나머지는 구멍.
export function multiPolygonArea(multiPolygon) {
  return multiPolygon.reduce(
    (total, polygon) =>
      total + polygon.reduce((area, ring, index) => area + (index === 0 ? ringArea(ring) : -ringArea(ring)), 0),
    0,
  );
}

export function multiPolygonBox(multiPolygon) {
  const box = { minLat: Infinity, maxLat: -Infinity, minLon: Infinity, maxLon: -Infinity };
  for (const polygon of multiPolygon) {
    for (const [lon, lat] of polygon[0]) {
      box.minLat = Math.min(box.minLat, lat);
      box.maxLat = Math.max(box.maxLat, lat);
      box.minLon = Math.min(box.minLon, lon);
      box.maxLon = Math.max(box.maxLon, lon);
    }
  }
  return box;
}

export function vertexCount(multiPolygon) {
  return multiPolygon.reduce((total, polygon) => total + polygon.reduce((count, ring) => count + ring.length, 0), 0);
}
