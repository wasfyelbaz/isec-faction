import { useState } from 'react';
import { Calculator } from 'lucide-react';
import { marked } from 'marked';
import type { AssessmentChecklist, PeerReviewVulnerability, ScoringType, VulnerabilityChecklistItem, VulnerabilitySeverity } from '../types';
import { Button, CvssCalculator, SeverityBadge } from '../components';
import type { CvssApplyResult } from '../components';
import TrackChangesEditor from '../components/TrackChangesEditor';
import PlainEditor from '../components/PlainEditor';
import SeverityLevelSelect from '../components/SeverityLevelSelect';
import { MultiSelect } from '../components/SearchableSelect';
import '../components/SearchableSelect.css';
import { SEVERITY_COLORS } from '../utils/vulnSeverity';
import { useTerminology } from '../context/TerminologyContext';
import { checklistItemKey, sameRating, vulnAttributeChanges, vulnTemplateFields } from '../utils/peerReviewFinding';

function PrCvssScoreBadge({ score, severity }: { score?: number; severity: VulnerabilitySeverity }) {
  const { severityLabel } = useTerminology();
  const color = SEVERITY_COLORS[severity] ?? '#9ca3af';
  return (
    <div className="pr-cvss-badge">
      <div className="pr-cvss-badge-value" style={{ color }}>
        {score != null ? score.toFixed(1) : '—'}
      </div>
      <div className="pr-cvss-badge-label" style={{ background: color }}>
        {severityLabel(severity)}
      </div>
    </div>
  );
}

// Convert a markdown snapshot to HTML for the first load; a saved revision is already HTML.
function getInitialHtml(snapshot: string | undefined, revised?: string): string {
  if (revised) return revised;
  return String(marked.parse(snapshot || ''));
}

export interface PeerReviewFindingBlockProps {
  vuln: PeerReviewVulnerability;
  readOnly: boolean;
  scoringType: ScoringType;
  /** The assessment's report sections; empty when sections are not in use. */
  sections: string[];
  /** The checklists attached to the assessment, whose items a finding is filed under. */
  checklists: AssessmentChecklist[];
  userId: string;
  userName: string;
  /** Lock key for a region of this finding. */
  regionKey: (...parts: string[]) => string;
  onLocalEdit: (key: string) => void;
  heldByOther: (key: string) => string | undefined;
  /** Applies a change to this finding's review record. */
  onChange: (patch: Partial<PeerReviewVulnerability>) => void;
  onFieldValueChange: (fieldId: string, value: string) => void;
  onFieldNoteChange: (fieldId: string, value: string) => void;
}

/** One finding in the peer review editor: every part of it, each revisable, with notes. */
export default function PeerReviewFindingBlock({
  vuln, readOnly, scoringType, sections, checklists, userId, userName,
  regionKey, onLocalEdit, heldByOther, onChange, onFieldValueChange, onFieldNoteChange,
}: PeerReviewFindingBlockProps) {
  const [showCvssCalc, setShowCvssCalc] = useState(false);
  const cvssVersion = scoringType === 'CVSS_31' ? '3.1' : scoringType === 'CVSS_40' ? '4.0' : null;

  const attrKey = regionKey('attributes');
  const attrLockedBy = heldByOther(attrKey);
  const attrDisabled = readOnly || !!attrLockedBy;
  const changed = new Map(vulnAttributeChanges(vuln).map(c => [c.key, c]));

  /** A revision equal to the snapshot is no revision, so it is cleared rather than stored. */
  const revise = <K extends keyof PeerReviewVulnerability>(
    field: K, value: PeerReviewVulnerability[K], original: unknown,
    same: (a: unknown, b: unknown) => boolean = (a, b) => a === b,
  ) => {
    onLocalEdit(attrKey);
    onChange({ [field]: same(value, original) ? undefined : value } as Partial<PeerReviewVulnerability>);
  };
  const ratingSame = (a: unknown, b: unknown) => sameRating(a as string, b as string);

  const was = (key: string) => {
    const c = changed.get(key);
    return c ? <span className="pr-attr-was" title={c.from}>was: {c.from.split('\n').join(', ')}</span> : null;
  };

  const severity = vuln.revisedSeverity ?? vuln.severity;
  const cvssScore = vuln.revisedCvssString != null ? vuln.revisedCvssScore : vuln.cvssScore;
  const cvssString = vuln.revisedCvssString ?? vuln.cvssString;

  const handleCvssApply = (result: CvssApplyResult) => {
    const score = cvssVersion === '3.1' ? result.v31.score : result.v40.score;
    const vector = cvssVersion === '3.1' ? result.v31.vectorString : result.v40.vectorString;
    setShowCvssCalc(false);
    onLocalEdit(attrKey);
    const same = vector === (vuln.cvssString ?? '') && score === vuln.cvssScore;
    onChange(same
      ? { revisedCvssScore: undefined, revisedCvssString: undefined, revisedSeverity: undefined }
      : {
        revisedCvssScore: score,
        revisedCvssString: vector,
        revisedSeverity: result.severity === vuln.severity ? undefined : result.severity,
      });
  };

  const items = vuln.revisedChecklistItems ?? vuln.checklistItems ?? [];
  const handleChecklistChange = (templateId: string, keys: string[]) => {
    const cl = checklists.find(c => c.templateId === templateId);
    const others = items.filter(i => i.templateId !== templateId);
    const chosen: VulnerabilityChecklistItem[] = keys.map(k => {
      const questionId = k.split('::')[1];
      const r = cl?.responses.find(x => x.questionId === questionId);
      return { templateId, questionId, checklistName: cl?.templateName, questionText: r?.questionText };
    });
    const next = [...others, ...chosen];
    const original = vuln.checklistItems ?? [];
    const same = next.length === original.length
      && next.every(i => original.some(o => checklistItemKey(o) === checklistItemKey(i)));
    onLocalEdit(attrKey);
    onChange({ revisedChecklistItems: same ? undefined : next });
  };

  const richField = (
    label: string, editorKey: string, snapshot: string | undefined, revised: string | undefined,
    revisedField: keyof PeerReviewVulnerability, notesField: keyof PeerReviewVulnerability, required = false,
  ) => (
    <div className="pr-field-block">
      <div className="pr-col-header">
        <div className="pr-field-label">
          {label}{required && <span className="required-mark"> *</span>}
        </div>
        <span className="pr-notes-col-label">Notes</span>
      </div>
      <div className="pr-field-with-notes">
        <TrackChangesEditor
          key={`${vuln.vulnerabilityId}-${editorKey}`}
          defaultValue={getInitialHtml(snapshot, revised)}
          onChange={val => {
            onLocalEdit(regionKey(String(revisedField)));
            onChange({ [revisedField]: val } as Partial<PeerReviewVulnerability>);
          }}
          userId={userId}
          userName={userName}
          disabled={readOnly}
          lockedBy={heldByOther(regionKey(String(revisedField)))}
        />
        <PlainEditor
          defaultValue={(vuln[notesField] as string | undefined) || ''}
          onChange={val => {
            onLocalEdit(regionKey(String(notesField)));
            onChange({ [notesField]: val } as Partial<PeerReviewVulnerability>);
          }}
          disabled={readOnly}
          lockedBy={heldByOther(regionKey(String(notesField)))}
        />
      </div>
    </div>
  );

  return (
    <div className="pr-vuln-block">
      <div className="pr-vuln-header">
        <strong>{vuln.revisedName?.trim() || vuln.name}</strong>
        <SeverityBadge severity={severity} />
        {(vuln.revisedLikelihood ?? vuln.likelihood) && (
          <span className="pr-vuln-rated-field">
            <span className="pr-vuln-rated-label">Likelihood</span>
            <SeverityBadge severity={vuln.revisedLikelihood ?? vuln.likelihood} />
          </span>
        )}
        {(vuln.revisedImpact ?? vuln.impact) && (
          <span className="pr-vuln-rated-field">
            <span className="pr-vuln-rated-label">Impact</span>
            <SeverityBadge severity={vuln.revisedImpact ?? vuln.impact} />
          </span>
        )}
      </div>

      {/* ── Attributes: everything about the finding that is not prose ── */}
      <div className="pr-field-block">
        <div className="pr-col-header">
          <div className="pr-field-label">Finding Details</div>
          <span className="pr-notes-col-label">Notes</span>
        </div>
        <div className="pr-field-with-notes">
          <div className="pr-attr-grid" title={attrLockedBy ? `${attrLockedBy} is editing` : undefined}>
            <label className="pr-attr pr-attr--wide">
              <span className="pr-attr-label">Title</span>
              <input
                className="pr-text-input"
                value={vuln.revisedName ?? vuln.name}
                onChange={e => revise('revisedName', e.target.value, vuln.name)}
                disabled={attrDisabled}
              />
              {was('name')}
            </label>

            {scoringType === 'NATIVE' ? (
              <div className="pr-attr">
                <span className="pr-attr-label">Severity</span>
                <SeverityLevelSelect
                  value={severity}
                  onChange={v => revise('revisedSeverity', v as VulnerabilitySeverity, vuln.severity)}
                  disabled={attrDisabled}
                />
                {was('severity')}
              </div>
            ) : (
              <div className="pr-attr pr-attr--wide">
                <span className="pr-attr-label">CVSS {cvssVersion}</span>
                <div className="pr-vuln-scoring">
                  <PrCvssScoreBadge score={cvssScore} severity={severity} />
                  <div className="pr-vuln-cvss-meta">
                    <span className="pr-vuln-cvss-string">{cvssString || '—'}</span>
                    {!attrDisabled && (
                      <Button size="sm" variant="secondary" onClick={() => setShowCvssCalc(true)}>
                        <Calculator size={13} />
                        Revise CVSS
                      </Button>
                    )}
                  </div>
                </div>
                {was('cvss')}
              </div>
            )}

            <div className="pr-attr">
              <span className="pr-attr-label">Likelihood</span>
              <SeverityLevelSelect
                value={vuln.revisedLikelihood ?? vuln.likelihood ?? ''}
                onChange={v => revise('revisedLikelihood', v, vuln.likelihood ?? '', ratingSame)}
                disabled={attrDisabled}
                allowEmpty
                emptyLabel="— None —"
              />
              {was('likelihood')}
            </div>
            <div className="pr-attr">
              <span className="pr-attr-label">Impact rating</span>
              <SeverityLevelSelect
                value={vuln.revisedImpact ?? vuln.impact ?? ''}
                onChange={v => revise('revisedImpact', v, vuln.impact ?? '', ratingSame)}
                disabled={attrDisabled}
                allowEmpty
                emptyLabel="— None —"
              />
              {was('impact')}
            </div>

            <label className="pr-attr pr-attr--wide">
              <span className="pr-attr-label">Asset / Location</span>
              <input
                className="pr-text-input"
                value={vuln.revisedAssetLocation ?? vuln.assetLocation ?? ''}
                onChange={e => revise('revisedAssetLocation', e.target.value, vuln.assetLocation ?? '')}
                disabled={attrDisabled}
              />
              {was('assetLocation')}
            </label>

            {sections.length > 0 && (
              <label className="pr-attr">
                <span className="pr-attr-label">Section</span>
                <select
                  className="pr-text-input"
                  value={vuln.revisedSection ?? vuln.section ?? ''}
                  onChange={e => revise('revisedSection', e.target.value, vuln.section ?? '')}
                  disabled={attrDisabled}
                >
                  <option value="">— Unsectioned —</option>
                  {sections.map(s => <option key={s} value={s}>{s}</option>)}
                </select>
                {was('section')}
              </label>
            )}

            {checklists.map(cl => (
              <div className="pr-attr pr-attr--wide" key={cl.id}>
                <span className="pr-attr-label">{cl.templateName}</span>
                <MultiSelect
                  selected={items.filter(i => i.templateId === cl.templateId).map(checklistItemKey)}
                  onChange={keys => handleChecklistChange(cl.templateId, keys)}
                  options={[...(cl.responses || [])]
                    .sort((a, b) => (a.order ?? 0) - (b.order ?? 0))
                    .map(r => ({ value: `${cl.templateId}::${r.questionId}`, label: r.questionText }))}
                  placeholder="Select items…"
                  countLabel="items selected"
                  disabled={attrDisabled}
                />
              </div>
            ))}
            {changed.has('checklistItems') && (
              <div className="pr-attr pr-attr--wide">{was('checklistItems')}</div>
            )}
          </div>
          <PlainEditor
            defaultValue={vuln.attributesNotes || ''}
            onChange={val => {
              onLocalEdit(regionKey('attributesNotes'));
              onChange({ attributesNotes: val });
            }}
            disabled={readOnly}
            lockedBy={heldByOther(regionKey('attributesNotes'))}
          />
        </div>
      </div>

      {richField('Description', 'desc', vuln.description, vuln.revisedDescription, 'revisedDescription', 'descriptionNotes')}
      {richField('Impact', 'impact', vuln.impactNarrative, vuln.revisedImpactNarrative, 'revisedImpactNarrative', 'impactNarrativeNotes', true)}
      {richField('Recommendation', 'rec', vuln.recommendation, vuln.revisedRecommendation, 'revisedRecommendation', 'recommendationNotes')}
      {richField('Proof Of Concept', 'details', vuln.details, vuln.revisedDetails, 'revisedDetails', 'detailsNotes')}

      {/* ── Template fields ── */}
      {vulnTemplateFields(vuln).map(field => {
        const valueKey = regionKey('field', field.id);
        const noteKey = regionKey('field', field.id, 'notes');
        const original = vuln.fieldValues?.[field.id];
        const revised = vuln.revisedFieldValues?.[field.id];
        const notes = (
          <PlainEditor
            defaultValue={vuln.fieldNotes?.[field.id] || ''}
            onChange={val => {
              onLocalEdit(noteKey);
              onFieldNoteChange(field.id, val);
            }}
            disabled={readOnly}
            lockedBy={heldByOther(noteKey)}
          />
        );
        return (
          <div key={field.id} className="pr-field-block">
            <div className="pr-col-header">
              <div className="pr-field-label">{field.displayName}</div>
              <span className="pr-notes-col-label">Notes</span>
            </div>
            <div className="pr-field-with-notes">
              {field.fieldType === 'RICH_TEXT' ? (
                <TrackChangesEditor
                  key={`${vuln.vulnerabilityId}-field-${field.id}`}
                  defaultValue={getInitialHtml(original, revised)}
                  onChange={val => {
                    onLocalEdit(valueKey);
                    onFieldValueChange(field.id, val);
                  }}
                  userId={userId}
                  userName={userName}
                  disabled={readOnly}
                  lockedBy={heldByOther(valueKey)}
                />
              ) : (
                <div className="pr-attr">
                  {field.fieldType === 'DROPDOWN' && field.dropdownOptions?.length ? (
                    <select
                      className="pr-text-input"
                      value={revised ?? original ?? ''}
                      onChange={e => {
                        onLocalEdit(valueKey);
                        onFieldValueChange(field.id, e.target.value);
                      }}
                      disabled={readOnly || !!heldByOther(valueKey)}
                    >
                      <option value="">—</option>
                      {field.dropdownOptions.map(o => <option key={o} value={o}>{o}</option>)}
                    </select>
                  ) : (
                    <input
                      className="pr-text-input"
                      value={revised ?? original ?? ''}
                      onChange={e => {
                        onLocalEdit(valueKey);
                        onFieldValueChange(field.id, e.target.value);
                      }}
                      disabled={readOnly || !!heldByOther(valueKey)}
                      title={heldByOther(valueKey) ? `${heldByOther(valueKey)} is editing` : undefined}
                    />
                  )}
                  {revised != null && revised !== (original ?? '') && (
                    <span className="pr-attr-was">was: {original || '—'}</span>
                  )}
                </div>
              )}
              {notes}
            </div>
          </div>
        );
      })}

      {cvssVersion && (
        <CvssCalculator
          isOpen={showCvssCalc}
          onClose={() => setShowCvssCalc(false)}
          onApply={handleCvssApply}
          lockedVersion={cvssVersion}
          initialVector={cvssString || undefined}
        />
      )}
    </div>
  );
}
