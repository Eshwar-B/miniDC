import React, { useState } from 'react';
import { api } from '../api/client';
import { LeaseResponse } from '../api/types';
import { Countdown } from './Countdown';

interface LeaseCardProps {
  lease: LeaseResponse;
  isLender?: boolean;
  onActionComplete?: () => void;
}

export const LeaseCard: React.FC<LeaseCardProps> = ({ lease, isLender, onActionComplete }) => {
  const [connecting, setConnecting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleConnect = async () => {
    setConnecting(true);
    setError(null);
    try {
      const res = await api.getConnectUrl(lease.id);
      window.open(res.url, '_blank', 'noopener,noreferrer');
    } catch (err: any) {
      setError(err.message || 'Failed to open desktop connection');
    } finally {
      setConnecting(false);
    }
  };

  const handleEnd = async () => {
    if (!window.confirm('Are you sure you want to end this lease early?')) {
      return;
    }
    try {
      await api.endLease(lease.id);
      if (onActionComplete) onActionComplete();
    } catch (err: any) {
      setError(err.message || 'Failed to end lease');
    }
  };

  const handleKill = async () => {
    const reason = prompt('Enter termination reason:') || 'Terminated by lender';
    try {
      await api.killLease(lease.id, reason);
      if (onActionComplete) onActionComplete();
    } catch (err: any) {
      setError(err.message || 'Failed to kill lease');
    }
  };

  const getStateClass = (state: string) => {
    switch (state) {
      case 'ACTIVE': return 'badge-success';
      case 'PROVISIONING': return 'badge-warning';
      case 'REQUESTED': return 'badge-info';
      case 'EXPIRING': return 'badge-warning';
      case 'ENDED': return 'badge-muted';
      case 'FAILED': return 'badge-danger';
      default: return 'badge-secondary';
    }
  };

  return (
    <div className="card lease-card">
      <div className="lease-card-header">
        <h4>{lease.vmName || `Lease ${lease.id.substring(0, 8)}`}</h4>
        <span className={`badge ${getStateClass(lease.state)}`}>{lease.state}</span>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      <div className="lease-details">
        <p><strong>RAM:</strong> {lease.ramMb} MB</p>
        <p><strong>CPU:</strong> {lease.cores} cores</p>
        <p><strong>Disk:</strong> {lease.diskGb} GB</p>
        <p><strong>Duration:</strong> {lease.requestedHours} hours</p>
        {lease.vmPort && <p><strong>Host Port:</strong> {lease.vmPort}</p>}
        {lease.failReason && <p className="text-danger"><strong>Error:</strong> {lease.failReason}</p>}
      </div>

      {(lease.state === 'ACTIVE' || lease.state === 'EXPIRING') && (
        <div className="lease-timer">
          <span>Time Remaining: </span>
          <Countdown endsAt={lease.endsAt} />
        </div>
      )}

      <div className="lease-card-actions">
        {lease.state === 'ACTIVE' && (
          <button
            className="btn btn-primary"
            onClick={handleConnect}
            disabled={connecting}
          >
            {connecting ? 'Connecting...' : 'Open Desktop'}
          </button>
        )}

        {(lease.state === 'ACTIVE' || lease.state === 'REQUESTED' || lease.state === 'APPROVED') && !isLender && (
          <button className="btn btn-secondary" onClick={handleEnd}>
            {lease.state === 'ACTIVE' ? 'End Lease' : 'Cancel Request'}
          </button>
        )}

        {isLender && (lease.state === 'ACTIVE' || lease.state === 'PROVISIONING' || lease.state === 'REQUESTED') && (
          <button className="btn btn-danger" onClick={handleKill}>
            Kill VM
          </button>
        )}
      </div>
    </div>
  );
};

