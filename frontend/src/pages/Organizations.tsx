import { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Edit2, Trash2, Plus } from 'lucide-react';
import { organizationsApi } from '../api';
import type { Organization } from '../types';
import DataTable, { Column, PaginationInfo, SortState, sortParam } from '../components/DataTable';
import { usePersistedState } from '../hooks/usePersistedState';
import Page from '../components/Page';
import {
  Button,
  IconButton,
  ActionButtons,
  ErrorMessage,
  ConfirmDialog,
} from '../components';
import './Organizations.css';
import { useTerminology } from '../context/TerminologyContext';

// Table view state (search, filters, sort, page) is remembered under this key across navigation.
const TABLE_KEY = 'organizations';

export default function Organizations() {
  const { organizationLower, organizationSingular, organizationsLower } = useTerminology();
  const navigate = useNavigate();
  const [organizations, setOrganizations] = useState<Organization[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [pendingDelete, setPendingDelete] = useState<Organization | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState('');

  const [pagination, setPagination] = usePersistedState<PaginationInfo>(TABLE_KEY, 'pagination', {
    page: 0,
    pageSize: 10,
    total: 0,
    totalPages: 0,
  });

  const [searchQuery, setSearchQuery] = usePersistedState(TABLE_KEY, 'searchQuery', '');
  const [sort, setSort] = usePersistedState<SortState | null>(TABLE_KEY, 'sort', null);

  // Get user permissions
  const user = JSON.parse(localStorage.getItem('user') || '{}');
  const authorities = user.authorities || [];
  const canCreate = authorities.includes('super_admin') || authorities.includes('organizations:create:all');
  const canEdit = authorities.includes('super_admin') || authorities.includes('organizations:edit:all') || authorities.includes('organizations:read:owned');
  const canDelete = authorities.includes('super_admin') || authorities.includes('organizations:delete:all');

  useEffect(() => {
    loadOrganizations();
  }, [pagination.page, pagination.pageSize, searchQuery, sort]);

  const loadOrganizations = async () => {
    try {
      setLoading(true);
      const response = await organizationsApi.getAll(
        pagination.page, pagination.pageSize, searchQuery, sortParam(sort));
      if (response.data) {
        setOrganizations(response.data);
        setPagination({
          page: response.pagination?.page || 0,
          pageSize: response.pagination?.size || 10,
          total: response.pagination?.totalElements || 0,
          totalPages: response.pagination?.totalPages || 0,
        });
      }
    } catch (err: any) {
      setError(err.response?.data?.message || `Failed to load ${organizationsLower}`);
    } finally {
      setLoading(false);
    }
  };

  const handleCreate = () => navigate('/organizations/new');

  const handleEdit = (organization: Organization) => {
    navigate(`/organizations/${organization.id}/edit`);
  };

  const handleDelete = async () => {
    if (!pendingDelete) return;
    setDeleting(true);
    setDeleteError('');
    try {
      await organizationsApi.delete(pendingDelete.id);
      setPendingDelete(null);
      await loadOrganizations();
    } catch (err: any) {
      // The server refuses while applications or member users still belong here, and says why.
      setDeleteError(err.response?.data?.message || `Failed to delete ${organizationLower}`);
      setPendingDelete(null);
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

  const columns: Column<Organization>[] = [
    {
      header: 'Name',
      sortKey: 'name',
      accessor: 'name',
    },
    {
      header: 'Description',
      sortKey: 'description',
      accessor: 'description',
    },
    {
      header: 'Actions',
      width: '120px',
      render: (organization) => (
        <ActionButtons>
          {canEdit && (
            <IconButton
              icon={Edit2}
              onClick={() => handleEdit(organization)}
              title="Edit"
              variant="edit"
            />
          )}
          {canDelete && (
            <IconButton
              icon={Trash2}
              onClick={() => { setDeleteError(''); setPendingDelete(organization); }}
              title="Delete"
              variant="delete"
            />
          )}
        </ActionButtons>
      ),
    },
  ];

  return (
    <Page className="organizations-page">
      <div className="page-header">
        <div />
        {canCreate && (
          <Button onClick={handleCreate} icon={Plus}>
            Add {organizationSingular}
          </Button>
        )}
      </div>

      {error && <ErrorMessage>{error}</ErrorMessage>}
      {deleteError && <ErrorMessage>{deleteError}</ErrorMessage>}

      <DataTable
        columns={columns}
        data={organizations}
        loading={loading}
        pagination={pagination}
        onPageChange={handlePageChange}
        onPageSizeChange={handlePageSizeChange}
        onSearchChange={handleSearchChange}
        initialSearch={searchQuery}
        searchPlaceholder={`Search ${organizationsLower}`}
        emptyMessage={`No ${organizationsLower} found`}
        idAccessor="id"
        sort={sort}
        onSortChange={handleSortChange}
      />

      <ConfirmDialog
        isOpen={!!pendingDelete}
        onClose={() => setPendingDelete(null)}
        onConfirm={handleDelete}
        title={`Delete ${organizationSingular}`}
        message={`Delete "${pendingDelete?.name}"? This cannot be undone.`}
        confirmText="Delete"
        variant="danger"
        isLoading={deleting}
      />
    </Page>
  );
}
