import React, { useEffect, useState } from 'react';
import { api } from '../api/client';
import { AuditLog, InviteResponse, LeaseResponse, Node } from '../api/types';

export const AdminPage: React.FC = () => {
  const [nodes, setNodes] = useState<Node[]>([]);
  const [leases, setLeases] = useState<LeaseResponse[]>([]);
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([]);
  const [createdInvite, setCreatedInvite] = useState<InviteResponse | null>(null);
  const [banUserId, setBanUserId] = useState<string>('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState<boolean>(true);

  const fetchAdminData = async () => {
    try {
      const [fetchedNodes, fetchedLeases, fetchedLogs] = await Promise.all([
        api.getAdminNodes(),
        api.getAdminLeases(),
        api.getAdminAudit(),
      ]);
      setNodes(fetchedNodes);
      setLeases(fetchedLeases);
      setAuditLogs(fetchedLogs);
    } catch (err: any) {
      setError(err.message || 'Failed fetching admin data');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAdminData();
  }, []);

  const handleGenerateInvite = async () => {
    try {
      const res = await api.createInvite();
      setCreatedInvite(res);
    } catch (err: any) {
      setError(err.message || 'Failed generating invite');
    }
  };

  const handleBanUser = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!banUserId.trim()) return;
    try {
      await api.banUser(banUserId.trim());
      alert('User banned successfully');
      setBanUserId('');
      fetchAdminData();
    } catch (err: any) {
      setError(err.message || 'Failed banning user');
    }
  };

  return (
    <div className="admin-page-container">
      <h2>System Administration</h2>
      <p className="subtitle">Platform governance, invite code provisioning, and immutable audit logs</p>

      {error && <div className="alert alert-error">{error}</div>}

      <div className="admin-grid">
        <div className="card">
          <h3>Create Registration Invite</h3>
          <p>Generate one-time 7-day valid registration tokens for new trusted users.</p>
          <button className="btn btn-primary" onClick={handleGenerateInvite}>Generate Invite Code</button>

          {createdInvite && (
            <div className="alert alert-success mt-3">
              <p><strong>Code:</strong> <code>{createdInvite.code}</code></p>
              <p><small>Expires: {new Date(createdInvite.expiresAt).toLocaleDateString()}</small></p>
            </div>
          )}
        </div>

        <div className="card">
          <h3>User Moderation</h3>
          <form onSubmit={handleBanUser}>
            <div className="form-group">
              <label>Target User UUID:</label>
              <input
                type="text"
                value={banUserId}
                onChange={(e) => setBanUserId(e.target.value)}
                placeholder="00000000-0000-0000-0000-000000000000"
                required
              />
            </div>
            <button type="submit" className="btn btn-danger">Ban User</button>
          </form>
        </div>
      </div>

      <div className="card mt-4">
        <h3>All Registered Compute Nodes ({nodes.length})</h3>
        <table className="data-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Name</th>
              <th>Status</th>
              <th>RAM</th>
              <th>Cores</th>
              <th>Disk</th>
              <th>IP / Address</th>
            </tr>
          </thead>
          <tbody>
            {nodes.map((n) => (
              <tr key={n.id}>
                <td><code>{n.id.substring(0, 8)}</code></td>
                <td>{n.name}</td>
                <td><span className={`badge ${n.status === 'ONLINE' ? 'badge-success' : 'badge-muted'}`}>{n.status}</span></td>
                <td>{n.totalRamMb ? `${n.totalRamMb} MB` : '-'}</td>
                <td>{n.totalCores ?? '-'}</td>
                <td>{n.totalDiskGb ? `${n.totalDiskGb} GB` : '-'}</td>
                <td>{n.address || '-'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="card mt-4">
        <h3>Platform Leases ({leases.length})</h3>
        <table className="data-table">
          <thead>
            <tr>
              <th>Lease ID</th>
              <th>VM Name</th>
              <th>State</th>
              <th>RAM / CPU</th>
              <th>Hours</th>
              <th>Port</th>
              <th>Updated</th>
            </tr>
          </thead>
          <tbody>
            {leases.map((l) => (
              <tr key={l.id}>
                <td><code>{l.id.substring(0, 8)}</code></td>
                <td>{l.vmName || '-'}</td>
                <td><span className="badge">{l.state}</span></td>
                <td>{l.ramMb}MB / {l.cores}c</td>
                <td>{l.requestedHours}h</td>
                <td>{l.vmPort || '-'}</td>
                <td>{new Date(l.updatedAt).toLocaleTimeString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="card mt-4">
        <h3>Audit Trail (Recent 100 Events)</h3>
        <table className="data-table">
          <thead>
            <tr>
              <th>Time</th>
              <th>Event</th>
              <th>Actor ID</th>
              <th>Lease ID</th>
              <th>Node ID</th>
              <th>Detail</th>
            </tr>
          </thead>
          <tbody>
            {auditLogs.map((log) => (
              <tr key={log.id}>
                <td><small>{new Date(log.at).toLocaleTimeString()}</small></td>
                <td><strong>{log.event}</strong></td>
                <td><small>{log.actorId ? log.actorId.substring(0, 8) : '-'}</small></td>
                <td><small>{log.leaseId ? log.leaseId.substring(0, 8) : '-'}</small></td>
                <td><small>{log.nodeId ? log.nodeId.substring(0, 8) : '-'}</small></td>
                <td>{log.detail || '-'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

