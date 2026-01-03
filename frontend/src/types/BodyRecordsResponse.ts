import type {BodyRecord} from "./BodyRecord.ts";

export interface BodyRecordsResponse {
  records: BodyRecord[];
  latest: BodyRecord | null;
}
