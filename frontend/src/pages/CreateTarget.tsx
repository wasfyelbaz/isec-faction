import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { X, Crosshair } from 'lucide-react';
import { applicationsApi, organizationsApi, entityFieldsApi, regionConfigApi } from '../api';
import type { ApplicationUrl, CreateApplicationRequest, Organization, UserDefinedField } from '../types';
import Page from '../components/Page';
import { Button, ConfirmDialog, FormLabel, IconButton, Input, Select, RichTextEditor } from '../components';
import { TARGET_TYPES } from '../utils/targetTypes';
import { useTerminology } from '../context/TerminologyContext';
import { usePageTitle } from '../context/PageTitleContext';
// Same page furniture as Create Assessment: title bar, form card on the left, preview on the right.
import './CreateAssessment.css';
import './Applications.css';

const COMMON_TECHNOLOGIES = [
  'Java', 'JavaScript', 'TypeScript', 'Python', 'C#', 'Go', 'Rust', 'PHP',
  'React', 'Angular', 'Vue', 'Node.js', 'Spring Boot', '.NET', 'Django', 'Flask',
  'PostgreSQL', 'MySQL', 'MongoDB', 'Redis', 'Oracle', 'SQL Server',
  'AWS', 'Azure', 'GCP', 'Docker', 'Kubernetes', 'Jenkins',
  'REST API', 'GraphQL', 'gRPC', 'Apache', 'Nginx', 'Tomcat',
];

// No App ID (the server numbers each target, e.g. ASMT-8), status, owner or stakeholders: iSec
// does not track them, so the form does not ask.
const EMPTY_FORM = {
  name: '',
  description: '',
  organizationId: '',
  region: 'Global',
  applicationType: '',
  assessmentFrequency: 'Ad Hoc',
};

/**
 * Creating a target, on its own page laid out like Create Assessment. It used to be a modal over
 * the Targets list; the form is long enough (URLs, stakeholders, technologies, custom fields) that
 * a page with room for a live summary beside it reads better.
 */
export default function CreateTarget() {
  const { organizationSingular, organizationLower, targetSingular, targetLower, targetPlural } = useTerminology();
  const navigate = useNavigate();
  const { setBreadcrumbs } = usePageTitle();

  useEffect(() => {
    setBreadcrumbs([
      { label: targetPlural, to: '/applications' },
      { label: `New ${targetSingular} / Asset` },
    ]);
    return () => setBreadcrumbs(null);
  }, [targetPlural, targetSingular]);

  const [formData, setFormData] = useState(EMPTY_FORM);
  const [urls, setUrls] = useState<ApplicationUrl[]>([]);
  const [newUrl, setNewUrl] = useState({ url: '', title: '' });
  const [technologies, setTechnologies] = useState<string[]>([]);
  const [newTechnology, setNewTechnology] = useState('');
  const [fieldDefinitions, setFieldDefinitions] = useState<UserDefinedField[]>([]);
  const [fieldValues, setFieldValues] = useState<Record<string, string>>({});

  const [organizations, setOrganizations] = useState<Organization[]>([]);
  const [regions, setRegions] = useState<string[]>([]);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [showCancelConfirm, setShowCancelConfirm] = useState(false);

  useEffect(() => {
    regionConfigApi.getRegions().then(setRegions).catch(() => {});
    organizationsApi.getAll(0, 1000)
      .then((res) => setOrganizations(res.data || []))
      .catch(() => setOrganizations([]));
    entityFieldsApi.getConfig('APPLICATION')
      .then((res) => setFieldDefinitions([...(res.data?.fieldDefinitions || [])]
        .sort((a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0))))
      .catch(() => setFieldDefinitions([]));
  }, []);

  const dirty = useMemo(() =>
    JSON.stringify(formData) !== JSON.stringify(EMPTY_FORM) || urls.length > 0 || technologies.length > 0
      || Object.values(fieldValues).some((v) => v && v.trim() !== ''),
  [formData, urls, technologies, fieldValues]);

  const handleAddUrl = () => {
    if (!newUrl.url || !newUrl.title) return;
    try {
      new URL(newUrl.url);
      setUrls([...urls, { ...newUrl }]);
      setNewUrl({ url: '', title: '' });
    } catch {
      setError('Please enter a valid URL (e.g., https://example.com)');
    }
  };

  const handleAddTechnology = () => {
    if (newTechnology && !technologies.includes(newTechnology)) {
      setTechnologies([...technologies, newTechnology]);
      setNewTechnology('');
    }
  };

  const toggleTechnology = (tech: string) =>
    setTechnologies(technologies.includes(tech)
      ? technologies.filter((t) => t !== tech)
      : [...technologies, tech]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSaving(true);
    try {
      const request: CreateApplicationRequest = {
        ...formData,
        urls: urls.length > 0 ? urls : undefined,
        technologies: technologies.length > 0 ? technologies : undefined,
        fieldValues: Object.keys(fieldValues).length > 0 ? fieldValues : undefined,
      } as CreateApplicationRequest;
      await applicationsApi.create(request);
      navigate('/applications');
    } catch (err: any) {
      setError(err.response?.data?.message || `Failed to create ${targetLower}`);
      setSaving(false);
    }
  };

  const handleCancel = () => {
    if (dirty) setShowCancelConfirm(true);
    else navigate('/applications');
  };

  const organizationName = organizations.find((o) => o.id === formData.organizationId)?.name;
  const regularFields = fieldDefinitions.filter((f) => f.fieldType !== 'RICH_TEXT');
  const richTextFields = fieldDefinitions.filter((f) => f.fieldType === 'RICH_TEXT');

  return (
    <Page variant="flush" fill className="create-assessment-page create-target-page">
      <div className="workflow-timeline-bar">
        <span className="wt-page-title">New {targetSingular} / Asset</span>
      </div>

      <div className="create-assessment-content">
        {error && (
          <div className="alert alert-danger alert-dismissible fade show mb-4" role="alert">
            {error}
            <button type="button" className="btn-close" onClick={() => setError('')}></button>
          </div>
        )}

        <div className="split-view">
          {/* Left side: the form */}
          <div className="form-panel">
            <form onSubmit={handleSubmit}>
              <div className="form-section">
                <h5 className="section-title">Basic Information</h5>
                <div className="row g-3">
                  <div className="col-md-6">
                    <FormLabel required>Name</FormLabel>
                    <Input
                      placeholder={`${targetSingular} name`}
                      value={formData.name}
                      onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                      required
                    />
                  </div>
                  <div className="col-md-6">
                    <FormLabel required>{targetSingular} Type</FormLabel>
                    <Select
                      value={formData.applicationType}
                      onChange={(e) => setFormData({ ...formData, applicationType: e.target.value })}
                      required
                    >
                      <option value="">Select a type</option>
                      {TARGET_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
                    </Select>
                  </div>
                  <div className="col-md-6">
                    <FormLabel required>{organizationSingular}</FormLabel>
                    <Select
                      value={formData.organizationId}
                      onChange={(e) => setFormData({ ...formData, organizationId: e.target.value })}
                      required
                    >
                      <option value="">{`Select a ${organizationLower}`}</option>
                      {organizations.map((org) => (
                        <option key={org.id} value={org.id}>{org.name}</option>
                      ))}
                    </Select>
                  </div>
                  <div className="col-md-6">
                    <FormLabel>Region</FormLabel>
                    <Select
                      value={formData.region}
                      onChange={(e) => setFormData({ ...formData, region: e.target.value })}
                    >
                      {regions.length === 0 && <option value="Global">Global</option>}
                      {regions.map((r) => <option key={r} value={r}>{r}</option>)}
                    </Select>
                  </div>
                  <div className="col-md-12">
                    <FormLabel>Description</FormLabel>
                    <RichTextEditor
                      value={formData.description}
                      onChange={(html) => setFormData({ ...formData, description: html })}
                    />
                  </div>
                </div>
              </div>

              <div className="form-section">
                <h5 className="section-title">URLs</h5>
                {urls.map((url, index) => (
                  <div key={index} className="list-item">
                    <div className="list-item-content">
                      <div className="font-medium">{url.title}</div>
                      <div className="text-sm text-muted">
                        <a href={url.url} target="_blank" rel="noopener noreferrer" className="link">{url.url}</a>
                      </div>
                    </div>
                    <IconButton icon={X} onClick={() => setUrls(urls.filter((_, i) => i !== index))}
                      variant="delete" title="Remove" />
                  </div>
                ))}
                <div className="technology-input-group">
                  <Input type="url" placeholder="URL" value={newUrl.url}
                    onChange={(e) => setNewUrl({ ...newUrl, url: e.target.value })} />
                  <Input placeholder="Title" value={newUrl.title}
                    onChange={(e) => setNewUrl({ ...newUrl, title: e.target.value })} />
                  <Button type="button" onClick={handleAddUrl} size="sm">Add</Button>
                </div>
              </div>

              <div className="form-section">
                <h5 className="section-title">Technologies</h5>
                {technologies.length > 0 && (
                  <div className="technology-tags" style={{ marginBottom: '1rem' }}>
                    {technologies.map((tech) => (
                      <div key={tech} className="technology-tag">
                        {tech}
                        <button type="button" onClick={() => toggleTechnology(tech)}><X size={12} /></button>
                      </div>
                    ))}
                  </div>
                )}
                <div className="technology-tags" style={{ marginBottom: '1rem' }}>
                  {COMMON_TECHNOLOGIES.map((tech) => (
                    <button
                      key={tech}
                      type="button"
                      onClick={() => toggleTechnology(tech)}
                      className={`technology-tag ${technologies.includes(tech) ? 'selected' : 'selectable'}`}
                      style={{ cursor: 'pointer' }}
                    >
                      {tech}
                    </button>
                  ))}
                </div>
                <div className="technology-input-group">
                  <Input
                    placeholder="Add custom technology"
                    value={newTechnology}
                    onChange={(e) => setNewTechnology(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        e.preventDefault();
                        handleAddTechnology();
                      }
                    }}
                  />
                  <Button type="button" onClick={handleAddTechnology} size="sm">Add</Button>
                </div>
              </div>

              <div className="form-section">
                <h5 className="section-title">Assessment Information</h5>
                <div className="row g-3">
                  <div className="col-md-6">
                    <FormLabel>Assessment Frequency</FormLabel>
                    <Select
                      value={formData.assessmentFrequency}
                      onChange={(e) => setFormData({ ...formData, assessmentFrequency: e.target.value })}
                    >
                      <option value="">Select frequency</option>
                      <option value="Ad Hoc">Ad Hoc</option>
                      <option value="Yearly">Yearly</option>
                      <option value="Custom">Custom</option>
                    </Select>
                  </div>
                </div>
              </div>

              {fieldDefinitions.length > 0 && (
                <div className="form-section">
                  <h5 className="section-title">Additional Information</h5>
                  <div className="row g-3">
                    {regularFields.map((field) => (
                      <div key={field.id} className="col-md-4">
                        <FormLabel required={!!field.required}>{field.displayName}</FormLabel>
                        {field.fieldType === 'DROPDOWN' ? (
                          <Select
                            value={fieldValues[field.id] || ''}
                            onChange={(e) => setFieldValues({ ...fieldValues, [field.id]: e.target.value })}
                          >
                            <option value="">Select...</option>
                            {(field.dropdownOptions || []).map((opt) => <option key={opt} value={opt}>{opt}</option>)}
                          </Select>
                        ) : (
                          <Input
                            value={fieldValues[field.id] || ''}
                            onChange={(e) => setFieldValues({ ...fieldValues, [field.id]: e.target.value })}
                            placeholder={field.helpText || `Enter ${field.displayName}`}
                          />
                        )}
                        {field.helpText && <small className="text-muted d-block mt-1">{field.helpText}</small>}
                      </div>
                    ))}
                    {richTextFields.map((field) => (
                      <div key={field.id} className="col-md-12">
                        <FormLabel required={!!field.required}>{field.displayName}</FormLabel>
                        <RichTextEditor
                          value={fieldValues[field.id] || ''}
                          onChange={(val) => setFieldValues({ ...fieldValues, [field.id]: val })}
                        />
                      </div>
                    ))}
                  </div>
                </div>
              )}

              <div className="form-actions">
                <div />
                <div className="button-group">
                  <Button type="button" variant="secondary" onClick={handleCancel} disabled={saving}>
                    Cancel
                  </Button>
                  <Button type="submit" variant="primary" disabled={saving}>
                    {saving ? 'Saving...' : `Create ${targetSingular}`}
                  </Button>
                </div>
              </div>
            </form>
          </div>

          {/* Right side: what is about to be created, where Create Assessment shows its calendar */}
          <div className="calendar-panel">
            <div className="calendar-sticky">
              <h5 className="section-title mb-3">Summary</h5>
              {formData.name || organizationName ? (
                <div className="target-summary">
                  <div className="target-summary-name">{formData.name || `Untitled ${targetLower}`}</div>
                  <div className="target-summary-meta">
                    {organizationName || `No ${organizationLower} selected`}
                  </div>
                  <dl className="target-summary-list">
                    <dt>Type</dt><dd>{formData.applicationType || '—'}</dd>
                    <dt>Region</dt><dd>{formData.region || '—'}</dd>
                    <dt>URLs</dt>
                    <dd>{urls.length === 0 ? '—' : urls.map((u) => <div key={u.url}>{u.url}</div>)}</dd>
                    <dt>Technologies</dt><dd>{technologies.length ? technologies.join(', ') : '—'}</dd>
                    <dt>Frequency</dt><dd>{formData.assessmentFrequency || '—'}</dd>
                  </dl>
                </div>
              ) : (
                <div className="text-center p-5 text-muted">
                  <Crosshair size={48} className="mb-3 opacity-25" />
                  <p className="mb-0">Fill in the name and {organizationLower} to see the {targetLower} summary</p>
                </div>
              )}
            </div>
          </div>
        </div>

        <ConfirmDialog
          isOpen={showCancelConfirm}
          onClose={() => setShowCancelConfirm(false)}
          onConfirm={() => navigate('/applications')}
          title="Unsaved Changes"
          message="You have unsaved changes. Are you sure you want to leave? All changes will be lost."
          confirmText="Yes, Leave"
          cancelText="Stay"
          variant="warning"
        />
      </div>
    </Page>
  );
}
