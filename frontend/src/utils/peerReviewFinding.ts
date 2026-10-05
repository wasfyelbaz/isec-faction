import type { PeerReviewVulnerability, UserDefinedField, VulnerabilityChecklistItem } from '../types';

/**
 * The parts of a finding a peer reviewer can revise besides its rich text, and how to tell
 * whether the reviewer changed them. Shared by the review editor and the assessor's diff so both
 * agree on what counts as a change — a revision equal to the snapshot never does.
 */

/** "templateId::questionId", how a checklist item is keyed in the dropdowns and compared. */
export const checklistItemKey = (i: Pick<VulnerabilityChecklistItem, 'templateId' | 'questionId'>) =>
  `${i.templateId}::${i.questionId}`;

function sameChecklistItems(a: VulnerabilityChecklistItem[] = [], b: VulnerabilityChecklistItem[] = []): boolean {
  const ka = new Set(a.map(checklistItemKey));
  const kb = new Set(b.map(checklistItemKey));
  return ka.size === kb.size && [...ka].every(k => kb.has(k));
}

function checklistLabel(items: VulnerabilityChecklistItem[] = []): string {
  if (items.length === 0) return 'None';
  return items.map(i => (i.checklistName ? `${i.checklistName}: ` : '') + (i.questionText || i.questionId)).join('\n');
}

export interface AttributeChange {
  /** The key accepted on the server for this attribute. */
  key: string;
  label: string;
  from: string;
  to: string;
}

const text = (v?: string | number | null) => (v == null || v === '' ? '—' : String(v));

/** Ratings are stored in either case ("High", "HIGH"); the case is not a reviewer's change. */
export const sameRating = (a?: string | null, b?: string | null) =>
  (a ?? '').toLowerCase() === (b ?? '').toLowerCase();

/** The attributes the reviewer changed, each with its before and after for display. */
export function vulnAttributeChanges(v: PeerReviewVulnerability): AttributeChange[] {
  const changes: AttributeChange[] = [];
  const add = (key: string, label: string, from?: string | number | null, to?: string | number | null) =>
    changes.push({ key, label, from: text(from), to: text(to) });

  if (v.revisedName != null && v.revisedName.trim() !== '' && v.revisedName.trim() !== v.name) {
    add('name', 'Title', v.name, v.revisedName.trim());
  }
  const cvssChanged = v.revisedCvssString != null
    && (v.revisedCvssString !== (v.cvssString ?? '') || v.revisedCvssScore !== v.cvssScore);
  if (cvssChanged) {
    add('cvss', 'CVSS', `${text(v.cvssScore)} ${v.cvssString ?? ''}`.trim(),
      `${text(v.revisedCvssScore)} ${v.revisedCvssString}`.trim());
  }
  // A CVSS change carries its own severity; only a standalone change is listed on its own.
  if (!cvssChanged && v.revisedSeverity != null && v.revisedSeverity !== v.severity) {
    add('severity', 'Severity', v.severity, v.revisedSeverity);
  }
  if (v.revisedLikelihood != null && !sameRating(v.revisedLikelihood, v.likelihood)) {
    add('likelihood', 'Likelihood', v.likelihood, v.revisedLikelihood);
  }
  if (v.revisedImpact != null && !sameRating(v.revisedImpact, v.impact)) {
    add('impact', 'Impact rating', v.impact, v.revisedImpact);
  }
  if (v.revisedAssetLocation != null && v.revisedAssetLocation !== (v.assetLocation ?? '')) {
    add('assetLocation', 'Asset / Location', v.assetLocation, v.revisedAssetLocation);
  }
  if (v.revisedSection != null && v.revisedSection !== (v.section ?? '')) {
    add('section', 'Section', v.section || 'Unsectioned', v.revisedSection || 'Unsectioned');
  }
  if (v.revisedChecklistItems != null && !sameChecklistItems(v.revisedChecklistItems, v.checklistItems)) {
    add('checklistItems', 'Checklist items', checklistLabel(v.checklistItems), checklistLabel(v.revisedChecklistItems));
  }
  return changes;
}

/**
 * The template fields to review on a finding: exactly the ones the finding form shows, which are
 * the finding's own field definitions. Values stored under any other key are not shown — they are
 * left over from a field since removed from the template, or duplicates an import wrote under the
 * field's name, and neither the form nor the report reads them. The Impact narrative used to be a
 * template field and is built in now, so a leftover definition of it is not offered twice.
 */
export function vulnTemplateFields(v: PeerReviewVulnerability): UserDefinedField[] {
  return (v.fieldDefinitions ?? []).filter(f => f.variableName !== 'impact_narrative');
}

export const fieldIsRich = (v: PeerReviewVulnerability, fieldId: string) =>
  (v.fieldDefinitions ?? []).some(f => f.id === fieldId && f.fieldType === 'RICH_TEXT');

export const fieldLabel = (v: PeerReviewVulnerability, fieldId: string) =>
  (v.fieldDefinitions ?? []).find(f => f.id === fieldId)?.displayName ?? fieldId;
