export interface BodyRecord {
  record_date: string;

  weight: number;
  weight_ma: number | null;
  weight_delta: number | null;

  body_fat_percentage: number;
  body_fat_ma: number | null;
  body_fat_delta: number | null;

  ecw_tbw_ratio: number;
}
