import { useEffect, useState } from "react";
import type { BodyApiResponse } from "../types/BodyApiResponse";

export function useBodyData() {
  const [data, setData] = useState<BodyApiResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    fetch("/api/body/stats")      .then((res) => res.json())
      .then((json: BodyApiResponse) => {
        setData(json);
        setLoading(false);
      })
      .catch((err) => {
        console.error("API error", err);
        setError(true);
        setLoading(false);
      });
  }, []);

  return { data, loading, error };
}
