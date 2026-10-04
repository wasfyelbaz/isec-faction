/**
 * What a target is. Stored in the target's `applicationType` (free text on the server), offered as
 * one list on the create page and the target's own page so both say the same thing.
 */
export const TARGET_TYPES = [
  'Web Application',
  'Mobile Application',
  'API',
  'Internal Network',
  'External Network',
  'Wireless Network',
  'Active Directory',
  'Cloud Infrastructure',
  'Thick Client',
  'IoT / Hardware',
  'Source Code Review',
  'Social Engineering',
  'Other',
] as const;

/** The list, plus a stored value from before it existed, so editing never drops what was saved. */
export function targetTypeOptions(current?: string): string[] {
  const list: string[] = [...TARGET_TYPES];
  return current && !list.includes(current) ? [current, ...list] : list;
}
