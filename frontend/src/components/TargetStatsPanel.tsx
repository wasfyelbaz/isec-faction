import { useMemo } from 'react';
import type { Assessment, Vulnerability } from '../types';
import SeverityPillCard from './SeverityPillCard';
import './TargetStatsPanel.css';

const ALL_SEVERITIES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFORMATIONAL'] as const;
const DAY_MS = 24 * 60 * 60 * 1000;

/**
 * The four cards at the top of a target's page: everything ever reported on it, how often it has
 * been assessed, the weakness that keeps coming back, and how long since it was last tested.
 *
 * Works from what the page has already loaded (the target's assessments and their findings), so
 * it adds no requests. A finding counts once its assessment is finalized (it has been "opened"),
 * fixed or not — the same set as the page's Vulnerabilities tab with closed findings shown.
 */
export default function TargetStatsPanel({ assessments, vulnerabilities, loading, frequency, customFrequencyMonths }: {
  assessments: Assessment[];
  vulnerabilities: Vulnerability[];
  loading: boolean;
  /** The target's Assessment Frequency ("Yearly", "Custom", "Ad Hoc"…), for the overdue hint. */
  frequency?: string;
  customFrequencyMonths?: number;
}) {
  const reported = useMemo(() => vulnerabilities.filter((v) => !!v.openedAt), [vulnerabilities]);

  const severityCounts = useMemo(() => {
    const counts: Record<string, number> = {};
    for (const v of reported) counts[v.severity] = (counts[v.severity] || 0) + 1;
    return counts;
  }, [reported]);

  const completedCount = assessments.filter((a) => a.completed).length;

  // Most common weakness: the category the most reported findings fall under.
  const weaknesses = useMemo(() => {
    const byName = new Map<string, number>();
    for (const v of reported) {
      const name = v.vulnerabilityCategory?.name?.trim();
      if (name) byName.set(name, (byName.get(name) || 0) + 1);
    }
    return [...byName.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]));
  }, [reported]);

  // Days since last test: since the most recently finished assessment. One still running is
  // shown alongside, since "tested 200 days ago" reads very differently while a test is underway.
  const lastTest = useMemo(() => {
    const finished = assessments
      .filter((a) => a.completed)
      .map((a) => ({ a, date: a.completedDate || a.plannedEndDate }))
      .filter((x): x is { a: Assessment; date: string } => !!x.date)
      .sort((x, y) => new Date(y.date).getTime() - new Date(x.date).getTime())[0];
    const now = Date.now();
    const running = assessments.find((a) =>
      !a.completed && a.startDate && new Date(a.startDate).getTime() <= now);
    const days = finished ? Math.max(0, Math.floor((now - new Date(finished.date).getTime()) / DAY_MS)) : null;
    const dueAfter = frequency === 'Yearly' ? 365
      : frequency === 'Custom' && customFrequencyMonths ? Math.round(customFrequencyMonths * 30.44)
      : null;
    return { finished, running, days, overdue: days !== null && dueAfter !== null && days > dueAfter, dueAfter };
  }, [assessments, frequency, customFrequencyMonths]);

  const fmt = (d: string) => new Date(d).toLocaleDateString();

  return (
    <div className="target-stats-grid">
      <SeverityPillCard
        title="Total Findings"
        counts={severityCounts}
        loading={loading}
        severities={ALL_SEVERITIES}
        emptyContent="No findings reported yet"
      />

      <div className="vdash-card">
        <div className="vdash-card-title">Total Assessments</div>
        {loading ? (
          <div className="vdash-card-body vdash-card-empty">Loading…</div>
        ) : (
          <div className="vdash-card-body tsp-body">
            <div className="vpill-total">{assessments.length}</div>
            <div className="tsp-sub">
              {completedCount} completed · {assessments.length - completedCount} in progress or scheduled
            </div>
          </div>
        )}
      </div>

      <div className="vdash-card">
        <div className="vdash-card-title">Most Common Weakness</div>
        {loading ? (
          <div className="vdash-card-body vdash-card-empty">Loading…</div>
        ) : weaknesses.length === 0 ? (
          <div className="vdash-card-body vdash-card-empty">No categorized findings yet</div>
        ) : (
          <div className="vdash-card-body tsp-body">
            <div className="tsp-headline" title={weaknesses[0][0]}>{weaknesses[0][0]}</div>
            <div className="tsp-sub">
              {weaknesses[0][1]} of {reported.length} finding{reported.length === 1 ? '' : 's'}
            </div>
            {weaknesses.length > 1 && (
              <ul className="tsp-runners">
                {weaknesses.slice(1, 3).map(([name, count]) => (
                  <li key={name}><span title={name}>{name}</span><strong>{count}</strong></li>
                ))}
              </ul>
            )}
          </div>
        )}
      </div>

      <div className="vdash-card">
        <div className="vdash-card-title">Days Since Last Test</div>
        {loading ? (
          <div className="vdash-card-body vdash-card-empty">Loading…</div>
        ) : lastTest.days === null ? (
          <div className="vdash-card-body tsp-body">
            <div className="tsp-headline">{lastTest.running ? 'Testing now' : 'Not tested yet'}</div>
            {lastTest.running && <div className="tsp-sub">{lastTest.running.name}</div>}
          </div>
        ) : (
          <div className="vdash-card-body tsp-body">
            <div className={`vpill-total${lastTest.overdue ? ' tsp-overdue' : ''}`}>{lastTest.days}</div>
            <div className="tsp-sub">
              {lastTest.finished!.a.name} finished {fmt(lastTest.finished!.date)}
            </div>
            {lastTest.overdue && (
              <div className="tsp-flag tsp-flag--warn">Due for a test ({frequency?.toLowerCase()} frequency)</div>
            )}
            {lastTest.running && <div className="tsp-flag">Testing now: {lastTest.running.name}</div>}
          </div>
        )}
      </div>
    </div>
  );
}
