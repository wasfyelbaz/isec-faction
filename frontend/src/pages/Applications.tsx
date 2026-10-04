import { useEffect, useState, useCallback, useMemo } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Eye, Trash2, Plus, Network, Upload, Download } from 'lucide-react';
import { applicationsApi, organizationsApi, subOrganizationsApi } from '../api';
import type { Application, Organization, SubOrganization, ApplicationImportResult } from '../types';
import DataTable, { Column, PaginationInfo, SortState, sortParam } from '../components/DataTable';
import { usePersistedState } from '../hooks/usePersistedState';
import { MultiSelect } from '../components/SearchableSelect';
import Page from '../components/Page';
import {
  Modal,
  Button,
  ConfirmDialog,
  IconButton,
  ActionButtons,
  Badge,
  FormGroup,
  FormLabel,
} from '../components';
import '../components/SearchableSelect.css';
import './Applications.css';
import { useTerminology } from '../context/TerminologyContext';

// Table view state (search, filters, sort, page) is remembered under this key across navigation.
const TABLE_KEY = 'applications';

export default function Applications() {
  const { organizationLower, organizationPlural, organizationsLower, organizationSingular,
    subOrganizationPlural, targetSingular, targetPlural, targetLower, targetsLower } = useTerminology();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  // The "All Assessments" tab that used to live here is retired: the same list is Scheduling's.
  // Old links still circulate — survey links posted in target discussions before the change
  // carried ?tab=assessments&assessment=&survey= — so send each to where it now lives.
  useEffect(() => {
    if (searchParams.get('tab') !== 'assessments') return;
    const assessmentId = searchParams.get('assessment');
    const surveyId = searchParams.get('survey');
    if (assessmentId) {
      navigate(`/assessments/${assessmentId}${surveyId ? `?survey=${encodeURIComponent(surveyId)}` : ''}`,
        { replace: true });
    } else {
      navigate('/scheduling', { replace: true });
    }
  }, [searchParams, navigate]);
  const [applications, setApplications] = useState<Application[]>([]);
  const [loading, setLoading] = useState(true);
  const [pendingDelete, setPendingDelete] = useState<Application | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState('');
  const [organizations, setOrganizations] = useState<Organization[]>([]);

  const user = JSON.parse(localStorage.getItem('user') || '{}');
  const authorities: string[] = user.authorities || [];

  const [pagination, setPagination] = usePersistedState<PaginationInfo>(TABLE_KEY, 'pagination', {
    page: 0,
    pageSize: 10,
    total: 0,
    totalPages: 0,
  });

  const [searchQuery, setSearchQuery] = usePersistedState(TABLE_KEY, 'searchQuery', '');
  const [sort, setSort] = usePersistedState<SortState | null>(TABLE_KEY, 'sort', null);
  // Multi-select filters, each matched as "any of". New field names, so a single value saved by the
  // earlier single-select build is simply ignored rather than mis-read.
  const [filterOrganizations, setFilterOrganizations] = usePersistedState<string[]>(TABLE_KEY, 'filterOrganizations', []);
  const [filterSubOrganizations, setFilterSubOrganizations] = usePersistedState<string[]>(TABLE_KEY, 'filterSubOrganizations', []);
  // Every division the user can see, so the picker works with or without an organization chosen.
  const [subOrganizations, setSubOrganizations] = useState<SubOrganization[]>([]);

  // CSV sync (admins only)
  const [showImport, setShowImport] = useState(false);
  const [importFile, setImportFile] = useState<File | null>(null);
  const [importing, setImporting] = useState(false);
  const [importResult, setImportResult] = useState<ApplicationImportResult | null>(null);
  const [importError, setImportError] = useState('');

  useEffect(() => {
    loadApplications();
    loadOrganizations();
  }, [pagination.page, pagination.pageSize, searchQuery, sort,
      filterOrganizations, filterSubOrganizations]);

  useEffect(() => {
    subOrganizationsApi.listAll()
      .then((res) => setSubOrganizations(res.data || []))
      .catch(() => setSubOrganizations([]));
  }, []);

  const loadApplications = async () => {
    try {
      setLoading(true);
      setError('');
      const response = await applicationsApi.getAll(
        pagination.page, pagination.pageSize, searchQuery, sortParam(sort),
        {
          organizationIds: filterOrganizations,
          subOrganizationIds: filterSubOrganizations,
        });
      if (response.data) {
        setApplications(response.data);
        setPagination({
          page: response.pagination?.page || 0,
          pageSize: response.pagination?.size || 10,
          total: response.pagination?.totalElements || 0,
          totalPages: response.pagination?.totalPages || 0,
        });
      }
    } catch (err: any) {
      setError(err.response?.data?.message || `Failed to load ${targetsLower}`);
    } finally {
      setLoading(false);
    }
  };

  const loadOrganizations = async () => {
    const canReadOrgs = authorities.includes('super_admin') ||
      authorities.some((a) => a.match(/^organizations:read/));
    if (!canReadOrgs) return;
    try {
      const response = await organizationsApi.getAll(0, 1000);
      if (response.data) {
        setOrganizations(response.data);
      }
    } catch (err: any) {
      console.error('Failed to load organizations:', err);
    }
  };

  const handleCreate = () => navigate('/applications/new');

  const handleEdit = (application: Application) => {
    navigate(`/applications/${application.id}/edit`);
  };

  const handleDelete = async () => {
    if (!pendingDelete) return;
    setDeleting(true);
    try {
      await applicationsApi.delete(pendingDelete.id);
      setPendingDelete(null);
      await loadApplications();
    } catch (err: any) {
      setError(err.response?.data?.message || `Failed to delete ${targetLower}`);
    } finally {
      setDeleting(false);
    }
  };

  const handlePageChange = useCallback((page: number) => {
    setPagination((prev) => ({ ...prev, page }));
  }, []);

  const handlePageSizeChange = useCallback((pageSize: number) => {
    setPagination((prev) => ({ ...prev, page: 0, pageSize }));
  }, []);

  const handleSearchChange = useCallback((search: string) => {
    setSearchQuery(search);
    setPagination((prev) => ({ ...prev, page: 0 }));
  }, []);

  // Re-sorting reshuffles the whole result set, so the current page number is
  // meaningless afterwards — go back to the first page.
  const handleSortChange = useCallback((next: SortState | null) => {
    setSort(next);
    setPagination((prev) => ({ ...prev, page: 0 }));
  }, []);

  const formatTechnologies = (technologies?: string[]) => {
    if (!technologies || technologies.length === 0) return 'None';
    return technologies.slice(0, 3).join(', ') + (technologies.length > 3 ? '...' : '');
  };

  const truncateDescription = (html: string) => {
    const text = html.replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim();
    return text.length > 100 ? text.slice(0, 100).trimEnd() + '…' : text;
  };

  const openImport = () => {
    setImportFile(null);
    setImportResult(null);
    setImportError('');
    setShowImport(true);
  };

  const handleDownloadTemplate = async () => {
    try {
      const blob = await applicationsApi.importTemplate();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'application-import-template.csv';
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
    } catch (err: any) {
      setImportError(err.response?.data?.message || 'Failed to download the template');
    }
  };

  const handleImport = async () => {
    if (!importFile) return;
    try {
      setImporting(true);
      setImportError('');
      const response = await applicationsApi.importCsv(importFile);
      setImportResult(response.data ?? null);
      // Rows already applied, so show the new state behind the dialog.
      loadApplications();
      loadOrganizations();
      subOrganizationsApi.listAll()
        .then((res) => setSubOrganizations(res.data || []))
        .catch(() => {});
    } catch (err: any) {
      setImportError(err.response?.data?.message || `Failed to sync ${targetsLower}`);
    } finally {
      setImporting(false);
    }
  };

  // Check permissions
  const canCreate = authorities.includes('super_admin') || authorities.includes('applications:create:all');
  const canEdit = authorities.includes('super_admin') || authorities.includes('applications:edit:all') || authorities.includes('applications:read:owned') || authorities.includes('applications:edit:org');
  const canDelete = authorities.includes('super_admin') || authorities.includes('applications:delete:all');
  const canView = authorities.includes('super_admin') || authorities.some((a) => a.startsWith('applications:read'));
  // The CSV sync writes across every organization, so it is administrators only — the same gate
  // the backend enforces.
  const isAdmin = authorities.includes('super_admin');

  // Divisions belong to an organization, so once one is picked only its own are offered. With no
  // organization chosen the names are ambiguous across organizations, so they carry the owner.
  const subOrgOptions = useMemo(() => {
    const scoped = filterOrganizations.length > 0
      ? subOrganizations.filter((sub) => filterOrganizations.includes(sub.organizationId))
      : subOrganizations;
    return scoped.map((sub) => ({
      value: sub.id,
      label: filterOrganizations.length === 1 || !sub.organizationName
        ? sub.name
        : `${sub.name} — ${sub.organizationName}`,
    }));
  }, [subOrganizations, filterOrganizations]);

  const orgNameById = useMemo(() => {
    const map: Record<string, string> = {};
    for (const org of organizations) map[org.id] = org.name;
    return map;
  }, [organizations]);

 const columns: Column<Application>[] = [
    {
      header: 'App ID',
      sortKey: 'appId',
      render: (app) => (
        <span className="font-mono" style={{ fontSize: '0.875rem', color: '#2563eb' }}>
          {app.appId || '—'}
        </span>
      ),
    },
    {
      header: 'Name',
      sortKey: 'name',
      render: (app) => (
        <div>
          <div className="font-medium">{app.name}</div>
          {app.description && (
            <div className="text-sm text-muted">
              {/* Descriptions are rich HTML now — show a plain-text summary in the table */}
              {truncateDescription(app.description)}
            </div>
          )}
        </div>
      ),
    },
    {
      header: organizationSingular,
      render: (app) => (app.organizationId && orgNameById[app.organizationId]) || '—',
    },
    {
      // Not sortable: the server has no sort key for the type.
      header: 'Type',
      render: (app) => app.applicationType || '—',
    },
    {
      header: 'Technologies',
      render: (app) => formatTechnologies(app.technologies),
    },
    {
      header: 'Total Assessments',
      render: (app) => app.assessmentCount ?? 0,
    },
    {
      header: 'Actions',
      render: (app) => (
        <ActionButtons>
          {(canEdit || canView) && (
            <IconButton
              icon={Eye}
              onClick={() => handleEdit(app)}
              title="View"
            />
          )}
          {canDelete && <IconButton icon={Trash2} onClick={() => setPendingDelete(app)} variant="delete" title="Delete" />}
          <IconButton icon={Network} onClick={() => {/* TODO: View connections */}} title="View Connections" />
        </ActionButtons>
      ),
    },
  ];

  return (
    <Page className="applications-page">
      {error && <div className="error-message">{error}</div>}

      {/* No tabs any more (assessments live under Scheduling); the bar keeps the page's actions. */}
      <div className="app-tab-nav app-tab-nav--actions-only">
        <div className="app-tab-actions">
          {isAdmin && (
            <Button variant="secondary" onClick={openImport} icon={Upload}>
              Sync from CSV
            </Button>
          )}
          {canCreate && (
            <Button onClick={handleCreate} icon={Plus}>
              New {targetSingular} / Asset
            </Button>
          )}
        </div>
      </div>

      <DataTable
          columns={columns}
          data={applications}
          loading={loading}
          pagination={pagination}
          onPageChange={handlePageChange}
          onPageSizeChange={handlePageSizeChange}
          onSearchChange={handleSearchChange}
          initialSearch={searchQuery}
          searchPlaceholder={`Search name, ID, ${organizationLower} or technology`}
          emptyMessage={`No ${targetsLower} found`}
          idAccessor="id"
          sort={sort}
          onSortChange={handleSortChange}
          onRowClick={(canEdit || canView) ? handleEdit : undefined}
          headerChildren={
            <div className="ss-filter-bar">
              <MultiSelect
                selected={filterOrganizations}
                onChange={(values) => {
                  setFilterOrganizations(values);
                  // A chosen division outside every selected organization can no longer match —
                  // drop it rather than filter by something the organization filter excludes.
                  if (values.length > 0) {
                    setFilterSubOrganizations((prev) => prev.filter((id) => {
                      const sub = subOrganizations.find((s) => s.id === id);
                      return !sub || values.includes(sub.organizationId);
                    }));
                  }
                  setPagination((prev) => ({ ...prev, page: 0 }));
                }}
                options={organizations.map((org) => ({ value: org.id, label: org.name }))}
                placeholder={`All ${organizationPlural}`}
              />
              <MultiSelect
                selected={filterSubOrganizations}
                onChange={(values) => {
                  setFilterSubOrganizations(values);
                  setPagination((prev) => ({ ...prev, page: 0 }));
                }}
                options={subOrgOptions}
                placeholder={`All ${subOrganizationPlural}`}
                searchable={false}
              />
            </div>
          }
        />

      <Modal
        isOpen={showImport}
        onClose={() => setShowImport(false)}
        title={`Sync ${targetPlural} from CSV`}
        size="lg"
        footer={
          <>
            <Button variant="secondary" onClick={() => setShowImport(false)}>
              {importResult ? 'Close' : 'Cancel'}
            </Button>
            <Button
              variant="primary"
              onClick={handleImport}
              disabled={!importFile || importing}
            >
              {importing ? 'Syncing…' : 'Sync'}
            </Button>
          </>
        }
      >
        <div className="app-import">
          <p className="app-import-lede">
            Each row is upserted: matched by <strong>appId</strong> first, then by{' '}
            <strong>name</strong>, and inserted when neither matches. {organizationPlural} and
            sub-organizations named in a row are created if they don't exist yet. Columns left out
            of the file are left untouched on the applications it updates.
          </p>
          <p className="app-import-lede">
            List columns hold several entries separated by <code>;</code>, with the parts of one
            entry separated by <code>|</code> — technologies are plain names
            (<code>Java;React</code>), URLs are <code>address|title</code>, and stakeholders are{' '}
            <code>name|email|role</code>. A filled-in list cell replaces the whole list.
          </p>

          <Button variant="secondary" icon={Download} onClick={handleDownloadTemplate}>
            Download Template
          </Button>

          <FormGroup>
            <FormLabel>CSV file</FormLabel>
            <input
              type="file"
              accept=".csv,text/csv"
              className="app-import-file"
              onChange={(e) => {
                setImportFile(e.target.files?.[0] ?? null);
                setImportResult(null);
                setImportError('');
              }}
            />
          </FormGroup>

          {importError && <div className="error-message">{importError}</div>}

          {importResult && (
            <div className="app-import-result">
              <div className="app-import-counts">
                <Badge variant="success">{importResult.created} created</Badge>
                <Badge variant="info">{importResult.updated} updated</Badge>
                <Badge variant={importResult.failed ? 'danger' : 'info'}>
                  {importResult.failed} failed
                </Badge>
                <span className="text-muted">of {importResult.processed} rows</span>
              </div>

              {importResult.createdOrganizations.length > 0 && (
                <p className="text-sm">
                  New {organizationsLower}: {importResult.createdOrganizations.join(', ')}
                </p>
              )}
              {importResult.createdSubOrganizations.length > 0 && (
                <p className="text-sm">
                  New sub-organizations: {importResult.createdSubOrganizations.join(', ')}
                </p>
              )}

              {importResult.errors.length > 0 && (
                <table className="app-import-errors">
                  <thead>
                    <tr><th>Line</th><th>Row</th><th>Problem</th></tr>
                  </thead>
                  <tbody>
                    {importResult.errors.map((e) => (
                      <tr key={`${e.line}-${e.message}`}>
                        <td>{e.line}</td>
                        <td>{e.identifier || '—'}</td>
                        <td>{e.message}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          )}
        </div>
      </Modal>

      <ConfirmDialog
        isOpen={!!pendingDelete}
        onClose={() => setPendingDelete(null)}
        onConfirm={handleDelete}
        title={`Delete ${targetSingular}`}
        message={`Delete "${pendingDelete?.name}"? This cannot be undone.`}
        confirmText="Delete"
        variant="danger"
        isLoading={deleting}
      />
    </Page>
  );
}
