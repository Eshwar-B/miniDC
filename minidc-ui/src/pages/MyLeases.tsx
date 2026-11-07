import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { LeaseResponse } from '../api/types';
import { LeaseCard } from '../components/LeaseCard';

export const MyLeases: React.FC = () => {
  const [leases, setLeases] = useState<LeaseResponse[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const fetchLeases = async () => {
    try {
      const myLeases = await api.getMyLeases();
      setLeases(myLeases);
    } catch (err: any) {
      setError(err.message || 'Failed to fetch leases');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchLeases();
    // Poll lease list every 10 seconds
    // TODO(phase-5): Real-time updates via WebSocket / STOMP
    const interval = setInterval(fetchLeases, 10000);
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="leases-page-container">
      <div className="section-header">
        <div>
          <h2>My Active Sandboxes</h2>
          <p className="subtitle">Launch, monitor, and manage your leased virtual environments</p>
        </div>
        <Link to="/market" className="btn btn-primary">+ New Lease</Link>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      {loading ? (
        <p>Loading your leases...</p>
      ) : leases.length === 0 ? (
        <div className="card text-center empty-card">
          <h3>No Leases Requested Yet</h3>
          <p>Browse available community nodes in the marketplace to spin up your first Ubuntu desktop VM.</p>
          <Link to="/market" className="btn btn-primary">Browse Marketplace</Link>
        </div>
      ) : (
        <div className="lease-grid">
          {leases.map((lease) => (
            <LeaseCard key={lease.id} lease={lease} onActionComplete={fetchLeases} />
          ))}
        </div>
      )}
    </div>
  );
};

