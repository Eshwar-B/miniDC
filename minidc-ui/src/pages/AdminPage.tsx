import React, { useEffect, useState } from 'react';
import { api } from '../api/client';
import { AbuseAlert, AuditLog, EgressStatus, InviteResponse, LeaseResponse, Node, TrafficVerificationResult } from '../api/types';

export const AdminPage: React.FC = () => {
  const [nodes, setNodes] = useState<Node[]>([]);
  const [leases, setLeases] = useState<LeaseResponse[]>([]);
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([]);
  const [abuseAlerts, setAbuseAlerts] = useState<AbuseAlert[]>([]);
  const [egressStatus, setEgressStatus] = useState<EgressStatus | null>(null);
  const [createdInvite, setCreatedInvite] = useState<InviteResponse | null>(null);
  const [banUserId, setBanUserId] = useState<string>('');
  const [verifyIp, setVerifyIp] = useState<string>('192.168.1.1');
  const [verifyPort, setVerifyPort] = useState<number>(80);
  const [verifyResult, setVerifyResult] = useState<TrafficVerificationResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState<boolean>(true);

  const fetchAdminData = async () => {
    try {
      const [fetchedNodes, fetchedLeases, fetchedLogs, fetchedEgress, fetchedAlerts] = await Promise.all([
        api.getAdminNodes(),
        api.getAdminLeases(),
        api.getAdminAudit(),
        api.getEgressStatus().catch(() => null),
        api.getAdminAbuseAlerts().catch(() => []),
      ]);
      setNodes(fetchedNodes);
      setLeases(fetchedLeases);
      setAuditLogs(fetchedLogs);
      if (fetchedEgress) setEgressStatus(fetchedEgress);
      if (fetchedAlerts) setAbuseAlerts(fetchedAlerts);
    } catch (err: any) {
      setError(err.message || 'Failed fetching admin data');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAdminData();
  }, []);

  const handleTestTraffic = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const res = await api.verifyTraffic(verifyIp.trim(), verifyPort);
      setVerifyResult(res);
    } catch (err: any) {
      setError(err.message || 'Traffic verification failed');
    }
  };

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
        <h3>Zero-Trust Egress & WireGuard Gateway</h3>
        <p className="text-muted">Lender home LAN isolation and centralized WireGuard exit gateway enforcement</p>

        {egressStatus && (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1rem', marginTop: '1rem' }}>
            <div style={{ background: '#111827', padding: '1rem', borderRadius: '6px', border: '1px solid #1f2937' }}>
              <div style={{ fontSize: '0.8rem', color: '#9ca3af' }}>Zero-Trust Status</div>
              <div style={{ fontSize: '1.1rem', fontWeight: 'bold', color: '#10b981' }}>ENFORCED</div>
              <div style={{ fontSize: '0.75rem', color: '#6b7280' }}>RFC 1918 LAN subnets blocked</div>
            </div>
            <div style={{ background: '#111827', padding: '1rem', borderRadius: '6px', border: '1px solid #1f2937' }}>
              <div style={{ fontSize: '0.8rem', color: '#9ca3af' }}>WireGuard Exit Gateway</div>
              <div style={{ fontSize: '1rem', fontWeight: 'bold', color: '#38bdf8' }}>{egressStatus.gatewayEndpoint}</div>
              <div style={{ fontSize: '0.75rem', color: '#6b7280' }}>Active peers: {egressStatus.activePeers}</div>
            </div>
            <div style={{ background: '#111827', padding: '1rem', borderRadius: '6px', border: '1px solid #1f2937' }}>
              <div style={{ fontSize: '0.8rem', color: '#9ca3af' }}>Anti-Spam Filter</div>
              <div style={{ fontSize: '1rem', fontWeight: 'bold', color: '#f59e0b' }}>BLOCKED</div>
              <div style={{ fontSize: '0.75rem', color: '#6b7280' }}>Ports: 25, 465, 587, 22</div>
            </div>
          </div>
        )}

        <div style={{ marginTop: '1.5rem', background: '#111827', padding: '1rem', borderRadius: '6px', border: '1px solid #1f2937' }}>
          <h4>Zero-Trust Policy Simulator</h4>
          <p style={{ fontSize: '0.85rem', color: '#9ca3af' }}>Test whether a destination IP and port are allowed through the sandbox egress boundary.</p>
          <form onSubmit={handleTestTraffic} style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', marginTop: '0.5rem' }}>
            <input
              type="text"
              value={verifyIp}
              onChange={(e) => setVerifyIp(e.target.value)}
              placeholder="Destination IP (e.g. 192.168.1.1 or 8.8.8.8)"
              style={{ flex: 2, padding: '0.4rem 0.6rem', background: '#1f2937', border: '1px solid #374151', color: '#fff', borderRadius: '4px' }}
              required
            />
            <input
              type="number"
              value={verifyPort}
              onChange={(e) => setVerifyPort(Number(e.target.value))}
              placeholder="Port"
              style={{ width: '90px', padding: '0.4rem 0.6rem', background: '#1f2937', border: '1px solid #374151', color: '#fff', borderRadius: '4px' }}
              required
            />
            <button type="submit" className="btn btn-primary" style={{ padding: '0.4rem 0.8rem' }}>Test Policy</button>
          </form>

          {verifyResult && (
            <div style={{
              marginTop: '0.75rem',
              padding: '0.75rem',
              borderRadius: '4px',
              background: verifyResult.allowed ? '#064e3b' : '#7f1d1d',
              border: `1px solid ${verifyResult.allowed ? '#059669' : '#dc2626'}`,
              color: '#fff',
              fontSize: '0.85rem'
            }}>
              <strong>{verifyResult.allowed ? '✓ TRAFFIC PERMITTED' : '✗ TRAFFIC DROPPED'}</strong>: {verifyResult.reason}
            </div>
          )}
        </div>
      </div>

      <div className="card mt-4">
        <h3>AI Anomaly & Sandbox Abuse Alerts ({abuseAlerts.length})</h3>
        <p className="text-muted">Real-time heuristics detecting cryptomining, resource starvation, and suspicious activity</p>

        {abuseAlerts.length === 0 ? (
          <p className="empty-hint" style={{ color: '#10b981' }}>✓ All active sandboxes operating within normal parameters. Zero abuse detected.</p>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>Time</th>
                <th>Type</th>
                <th>Confidence</th>
                <th>Lease ID</th>
                <th>Details</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {abuseAlerts.map((alert) => (
                <tr key={alert.alertId}>
                  <td><small>{new Date(alert.detectedAt).toLocaleTimeString()}</small></td>
                  <td><span className="badge badge-danger">{alert.type}</span></td>
                  <td><strong>{(alert.confidenceScore * 100).toFixed(0)}%</strong></td>
                  <td><code>{alert.leaseId.substring(0, 8)}</code></td>
                  <td>{alert.details}</td>
                  <td>
                    <span className={`badge ${alert.autoTerminated ? 'badge-danger' : 'badge-warning'}`}>
                      {alert.autoTerminated ? 'AUTO-TERMINATED' : 'FLAGGED'}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
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

