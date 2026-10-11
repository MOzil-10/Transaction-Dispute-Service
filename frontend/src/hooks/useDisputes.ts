import { useCallback, useEffect, useState } from "react";
import { disputeService } from "@/services/disputeService";
import { isDisputeOpen } from "@/lib/constants";
import type { DisputeResponse } from "@/types/api";

interface State {
  disputes: DisputeResponse[];
  loading: boolean;
  error: string | null;
  openCount: number;
  resolvedCount: number;
}

/** Loads all of the customer's disputes (small volume) and derives open/resolved counts. */
export function useDisputes() {
  const [state, setState] = useState<State>({
    disputes: [],
    loading: true,
    error: null,
    openCount: 0,
    resolvedCount: 0,
  });

  const load = useCallback(async () => {
    setState((s) => ({ ...s, loading: true, error: null }));
    try {
      const page = await disputeService.list(0, 100);
      const disputes = page.content;
      const openCount = disputes.filter((d) => isDisputeOpen(d.status)).length;
      setState({
        disputes,
        loading: false,
        error: null,
        openCount,
        resolvedCount: disputes.length - openCount,
      });
    } catch (e) {
      setState((s) => ({
        ...s,
        loading: false,
        error: e instanceof Error ? e.message : "Could not load disputes.",
      }));
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return { ...state, reload: load };
}