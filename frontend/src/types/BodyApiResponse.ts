import { BodyRecord } from "./BodyRecord";

export interface BodyProfile {
  gender: string | null;
  age: number | null;
  height_cm: number | null;
}

export interface BodyApiResponse {
  profile: BodyProfile | null;
  records: BodyRecord[];
}
