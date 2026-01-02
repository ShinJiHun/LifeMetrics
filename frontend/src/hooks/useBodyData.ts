import { useEffect, useState } from "react";
import { BodyApiResponse } from "../types/BodyApiResponse";

export function useBodyData() {
  const [data, setData] = useState<BodyApiResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    fetch("http://34.172.162.148:8000/api/body/stats")
      .then((res) => res.json())
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
