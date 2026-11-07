import React, { useEffect, useState } from 'react';
import { api } from '../api/client';
import { LeaseResponse, Node, OfferRequest } from '../api/types';
import { LeaseCard } from '../components/LeaseCard';
import { OfferForm } from '../components/OfferForm';

export const LenderDashboard: React.FC = () => {
  const [nodes, setNodes] = useState<Node[]>([]);
  const [incomingLeases, setIncomingLeases] = useState<LeaseResponse[]>([]);
  const [newNodeName, setNewNodeName] = useState('');
  const [createdToken, setCreatedToken] = useState<{ nodeId: string; token: string } | null>(null);
  const [showOfferForm, setShowOfferForm] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchData = async () => {
    try {
      const [fetchedNodes, fetchedLeases] = await Promise.all([
        api.getMyNodes(),
        api.getIncomingLeases(),
      ]);
      setNodes(fetchedNodes);
      setIncomingLeases(fetchedLeases);
    } catch (err: any) {
      setError(err.message || 'Failed loading dashboard data');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
    // Poll lease and node lists every 10 seconds
    // TODO(phase-5): Replace polling with live WebSocket/STOMP updates
    const interval = setInterval(fetchData, 10000);
    return () => clearInterval(interval);
  }, []);

  const handleRegisterNode = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newNodeName.trim()) return;

    try {
      const res = await api.createNode({ name: newNodeName });
      setCreatedToken({ nodeId: res.nodeId, token: res.agentToken });
      setNewNodeName('');
      fetchData();
    } catch (err: any) {
      setError(err.message || 'Failed creating node');
    }
  };

  const handleCreateOffer = async (data: OfferRequest) => {
    await api.createOffer(data);
    setShowOfferForm(false);
    alert('Offer published successfully!');
  };

  const handleApprove = async (leaseId: string) => {
    try {
      await api.approveLease(leaseId);
      fetchData();
    } catch (err: any) {
      setError(err.message || 'Approval failed');
    }
  };

  const handleReject = async (leaseId: string) => {
    const reason = prompt('Rejection reason:') || 'Declined by lender';
    try {
      await api.rejectLease(leaseId, reason);
      fetchData();
    } catch (err: any) {
      setError(err.message || 'Rejection failed');
    }
  };

  const handlePauseResume = async (node: Node) => {
    try {
      if (node.status === 'PAUSED') {
        await api.resumeNode(node.id);
      } else {
        await api.pauseNode(node.id);
      }
      fetchData();
    } catch (err: any) {
      setError(err.message || 'State update failed');
    }
  };

  return (
    <div className="dashboard-container">
      <h2>Lender Resource Hub</h2>
      <p className="subtitle">Manage idle machines, publish capacity, and approve incoming sandbox leases</p>

      {error && <div className="alert alert-error">{error}</div>}

      {createdToken && (
        <div className="alert alert-success token-alert">
          <h4>Node Registered! Copy your Agent Token now (shown only once):</h4>
          <p><strong>Node ID:</strong> <code>{createdToken.nodeId}</code></p>
          <p><strong>Agent Token:</strong> <code>{createdToken.token}</code></p>
          <button className="btn btn-secondary btn-sm" onClick={() => setCreatedToken(null)}>Dismiss</button>
        </div>
      )}

      <div className="dashboard-grid">
        <div className="section-card">
          <div className="section-header">
            <h3>Registered Compute Nodes</h3>
          </div>

          <form className="inline-form" onSubmit={handleRegisterNode}>
            <input
              type="text"
              value={newNodeName}
              onChange={(e) => setNewNodeName(e.target.value)}
              placeholder="Node name (e.g. Dell-Precision-Laptop)"
              required
            />
            <button type="submit" className="btn btn-primary">Add Node</button>
          </form>

          {nodes.length === 0 ? (
            <p className="empty-hint">No nodes registered yet. Register your first machine above.</p>
          ) : (
            <div className="node-list">
              {nodes.map((n) => (
                <div key={n.id} className="card node-card">
                  <div className="node-card-header">
                    <h4>{n.name}</h4>
                    <span className={`badge ${n.status === 'ONLINE' ? 'badge-success' : n.status === 'PAUSED' ? 'badge-warning' : 'badge-danger'}`}>
                      {n.status}
                    </span>
                  </div>
                  <div className="node-stats">
                    <p>RAM: {n.totalRamMb ? `${n.totalRamMb} MB` : 'Pending heartbeat'}</p>
                    <p>Cores: {n.totalCores ?? 'N/A'}</p>
                    <p>Disk: {n.totalDiskGb ? `${n.totalDiskGb} GB` : 'N/A'}</p>
                    <p>Address: {n.address || 'Unassigned'}</p>
                    <p className="text-muted"><small>Last seen: {n.lastHeartbeat ? new Date(n.lastHeartbeat).toLocaleTimeString() : 'Never'}</small></p>
                  </div>
                  <div className="node-actions">
                    <button className="btn btn-secondary btn-sm" onClick={() => handlePauseResume(n)}>
                      {n.status === 'PAUSED' ? 'Resume Node' : 'Pause Node'}
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="section-card">
          <div className="section-header">
            <h3>Publish Resource Offer</h3>
            {nodes.length > 0 && (
              <button className="btn btn-primary" onClick={() => setShowOfferForm(!showOfferForm)}>
                {showOfferForm ? 'Close Form' : '+ New Offer'}
              </button>
            )}
          </div>

          {showOfferForm && (
            <OfferForm
              nodes={nodes.filter((n) => n.status !== 'OFFLINE')}
              onSubmit={handleCreateOffer}
              onCancel={() => setShowOfferForm(false)}
            />
          )}

          <div className="incoming-leases">
            <h3>Incoming Lease Requests</h3>
            {incomingLeases.length === 0 ? (
              <p className="empty-hint">No active or pending lease requests.</p>
            ) : (
              <div className="lease-grid">
                {incomingLeases.map((lease) => (
                  <div key={lease.id} className="incoming-lease-wrapper">
                    <LeaseCard lease={lease} isLender={true} onActionComplete={fetchData} />
                    {lease.state === 'REQUESTED' && (
                      <div className="approval-controls">
                        <button className="btn btn-success" onClick={() => handleApprove(lease.id)}>Approve</button>
                        <button className="btn btn-danger" onClick={() => handleReject(lease.id)}>Reject</button>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

