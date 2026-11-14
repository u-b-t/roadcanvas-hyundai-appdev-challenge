// 앱의 TerritoryGrid(app/src/main/java/kr/roadcanvas/app/territory/TerritoryGrid.kt)와 같은 정사각형 격자 칸 계산.
// 칸 ID는 "{row}_{col}", 구역 ID는 두 번호를 8로 나눈 몫이다.
export const LAT_STEP = 0.00108;
export const LON_STEP = 0.00136;
export const REGION_CELLS = 8;

export function cellOf(lat, lon) {
  return { row: Math.floor((lat + 90) / LAT_STEP), col: Math.floor((lon + 180) / LON_STEP) };
}

export const cellId = ({ row, col }) => `${row}_${col}`;

export function regionOf(id) {
  const [row, col] = id.split('_').map(Number);
  return `${Math.floor(row / REGION_CELLS)}_${Math.floor(col / REGION_CELLS)}`;
}

const BASE = cellOf(37.5665, 126.978);

// 서울시청 칸에서 (row, col)만큼 떨어진 곳부터 rows x cols칸 블록
export function gridBlock(row, col, rows, cols) {
  const cells = [];
  for (let r = row; r < row + rows; r++) {
    for (let c = col; c < col + cols; c++) cells.push(cellId({ row: BASE.row + r, col: BASE.col + c }));
  }
  return cells;
}
