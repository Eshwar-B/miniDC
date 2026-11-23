import React, { useEffect, useState } from 'react';
import { api } from '../api/client';
import { VolumeInfo, WalletInfo, WalletTransactionInfo } from '../api/types';

export const StorageWalletPage: React.FC = () => {
  const [wallet, setWallet] = useState<WalletInfo | null>(null);
  const [transactions, setTransactions] = useState<WalletTransactionInfo[]>([]);
  const [volumes, setVolumes] = useState<VolumeInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState<string | null>(null);

  // New volume form state
  const [volName, setVolName] = useState('');
  const [volSize, setVolSize] = useState(20);
  const [mountPath, setMountPath] = useState('/home/borrower/workspace');

  // Estimator state
  const [estCores, setEstCores] = useState(2);
  const [estRam, setEstRam] = useState(4096);
  const [estDisk, setEstDisk] = useState(20);
  const [estHours, setEstHours] = useState(2);
  const [estimate, setEstimate] = useState<{ perMinuteCredits: number; totalEstimatedCredits: number } | null>(null);

  const loadData = async () => {
    try {
      setLoading(true);
      const [w, txs, vols] = await Promise.all([
        api.getWallet(),
        api.getWalletTransactions(),
        api.getMyVolumes(),
      ]);
      setWallet(w);
      setTransactions(txs);
      setVolumes(vols);
    } catch (e: any) {
      console.error('Failed to load wallet/volumes data', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  useEffect(() => {
    api.getBillingEstimate(estCores, estRam, estDisk, estHours)
      .then(setEstimate)
      .catch(() => {});
  }, [estCores, estRam, estDisk, estHours]);

  const handleTopUp = async (amount: number) => {
    try {
      const updated = await api.topUpWallet(amount);
      setWallet(updated);
      setMessage(`Successfully added ${amount} compute credits!`);
      const txs = await api.getWalletTransactions();
      setTransactions(txs);
    } catch (e: any) {
      setMessage(`Top-up failed: ${e.message}`);
    }
  };

  const handleCreateVolume = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await api.createVolume({ name: volName, sizeGb: volSize, mountPath });
      setVolName('');
      setMessage('Persistent volume created successfully!');
      const vols = await api.getMyVolumes();
      setVolumes(vols);
    } catch (e: any) {
      setMessage(`Creation failed: ${e.message}`);
    }
  };

  const handleSnapshot = async (id: string) => {
    try {
      const snap = await api.createVolumeSnapshot(id);
      setMessage(`Snapshot created: ${snap.snapshotRef}`);
      const vols = await api.getMyVolumes();
      setVolumes(vols);
    } catch (e: any) {
      setMessage(`Snapshot failed: ${e.message}`);
    }
  };

  const handleDeleteVolume = async (id: string) => {
    if (!window.confirm('Are you sure you want to delete this volume?')) return;
    try {
      await api.deleteVolume(id);
      setMessage('Volume deleted.');
      const vols = await api.getMyVolumes();
      setVolumes(vols);
    } catch (e: any) {
      setMessage(`Delete failed: ${e.message}`);
    }
  };

  if (loading) {
    return <div className="main-content"><p>Loading storage & wallet...</p></div>;
  }

  return (
    <div className="container" style={{ maxWidth: '1100px', margin: '0 auto', padding: '24px 16px' }}>
      <h2>Storage & Compute Economics</h2>
      <p className="text-muted">Manage persistent home directory volumes, state snapshots, and compute credit wallets.</p>

      {message && <div className="alert alert-info" style={{ margin: '16px 0' }}>{message}</div>}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '20px', marginTop: '20px' }}>
        
        {/* Wallet Overview Card */}
        <div className="card" style={{ padding: '20px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <h3>Credit Wallet</h3>
            <span className="badge badge-success" style={{ fontSize: '1rem', padding: '6px 12px' }}>
              🪙 {wallet?.balanceCredits || 0} Credits
            </span>
          </div>
          <p className="text-muted" style={{ marginTop: '8px', fontSize: '0.9rem' }}>
            Compute credits are deducted dynamically every minute of active lease runtime and credited directly to node lenders.
          </p>

          <div style={{ marginTop: '16px' }}>
            <label style={{ display: 'block', fontWeight: 600, marginBottom: '8px' }}>Instant Top-Up (Demo/Stripe):</label>
            <div style={{ display: 'flex', gap: '8px' }}>
              <button className="btn btn-secondary btn-sm" onClick={() => handleTopUp(100)}>+100 🪙</button>
              <button className="btn btn-primary btn-sm" onClick={() => handleTopUp(500)}>+500 🪙</button>
              <button className="btn btn-secondary btn-sm" onClick={() => handleTopUp(1000)}>+1,000 🪙</button>
            </div>
          </div>

          <div style={{ marginTop: '24px', borderTop: '1px solid rgba(255,255,255,0.08)', paddingTop: '16px' }}>
            <h4>Pricing Calculator</h4>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px', marginTop: '10px', fontSize: '0.85rem' }}>
              <div>
                <label>CPU Cores: {estCores}</label>
                <input type="range" min={1} max={16} value={estCores} onChange={e => setEstCores(Number(e.target.value))} style={{ width: '100%' }} />
              </div>
              <div>
                <label>RAM (MB): {estRam}</label>
                <input type="range" min={1024} max={32768} step={1024} value={estRam} onChange={e => setEstRam(Number(e.target.value))} style={{ width: '100%' }} />
              </div>
            </div>
            {estimate && (
              <div style={{ background: 'rgba(0,0,0,0.2)', padding: '10px', borderRadius: '6px', marginTop: '10px', fontSize: '0.85rem' }}>
                <p><strong>Rate:</strong> {estimate.perMinuteCredits} credits/min</p>
                <p><strong>Estimated Total ({estHours}h):</strong> {estimate.totalEstimatedCredits} credits</p>
              </div>
            )}
          </div>
        </div>

        {/* Create Volume Card */}
        <div className="card" style={{ padding: '20px' }}>
          <h3>Create Persistent Volume</h3>
          <p className="text-muted" style={{ fontSize: '0.9rem' }}>
            Encrypted volume mounted to `/home/borrower` retaining your code, configs, and models across separate lease sessions.
          </p>

          <form onSubmit={handleCreateVolume} style={{ marginTop: '14px', display: 'flex', flexDirection: 'column', gap: '12px' }}>
            <div>
              <label style={{ display: 'block', fontSize: '0.85rem', marginBottom: '4px' }}>Volume Name</label>
              <input
                type="text"
                className="form-control"
                placeholder="e.g., DeepLearning-Data"
                value={volName}
                onChange={e => setVolName(e.target.value)}
                required
              />
            </div>
            <div>
              <label style={{ display: 'block', fontSize: '0.85rem', marginBottom: '4px' }}>Capacity (GB)</label>
              <input
                type="number"
                min={1}
                max={500}
                className="form-control"
                value={volSize}
                onChange={e => setVolSize(Number(e.target.value))}
                required
              />
            </div>
            <div>
              <label style={{ display: 'block', fontSize: '0.85rem', marginBottom: '4px' }}>Mount Path</label>
              <input
                type="text"
                className="form-control"
                value={mountPath}
                onChange={e => setMountPath(e.target.value)}
                required
              />
            </div>
            <button type="submit" className="btn btn-primary" style={{ marginTop: '8px' }}>
              Create & Format Volume
            </button>
          </form>
        </div>
      </div>

      {/* Volumes List */}
      <div className="card" style={{ marginTop: '24px', padding: '20px' }}>
        <h3>My Persistent Storage Volumes</h3>
        {volumes.length === 0 ? (
          <p className="text-muted" style={{ marginTop: '10px' }}>No persistent volumes yet. Create one above to persist files across lease sessions.</p>
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '16px', marginTop: '16px' }}>
            {volumes.map(vol => (
              <div key={vol.id} style={{ background: 'rgba(0,0,0,0.2)', padding: '14px', borderRadius: '8px', border: '1px solid rgba(255,255,255,0.06)' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <h4>{vol.name}</h4>
                  <span className={`badge ${vol.status === 'ATTACHED' ? 'badge-success' : 'badge-secondary'}`}>{vol.status}</span>
                </div>
                <div style={{ fontSize: '0.85rem', marginTop: '8px', color: '#cbd5e1' }}>
                  <p><strong>Size:</strong> {vol.sizeGb} GB</p>
                  <p><strong>Mount Path:</strong> {vol.mountPath}</p>
                  {vol.lastSnapshotRef && (
                    <p style={{ wordBreak: 'break-all' }}><strong>Last Snapshot:</strong> {vol.lastSnapshotRef}</p>
                  )}
                </div>
                <div style={{ marginTop: '12px', display: 'flex', gap: '8px' }}>
                  <button className="btn btn-secondary btn-sm" onClick={() => handleSnapshot(vol.id)}>
                    Snapshot
                  </button>
                  {vol.status !== 'ATTACHED' && (
                    <button className="btn btn-danger btn-sm" onClick={() => handleDeleteVolume(vol.id)}>
                      Delete
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Wallet Transactions Table */}
      <div className="card" style={{ marginTop: '24px', padding: '20px' }}>
        <h3>Wallet Ledger Transactions</h3>
        {transactions.length === 0 ? (
          <p className="text-muted" style={{ marginTop: '10px' }}>No transactions recorded yet.</p>
        ) : (
          <table className="table" style={{ width: '100%', marginTop: '14px', fontSize: '0.9rem' }}>
            <thead>
              <tr style={{ textAlign: 'left', borderBottom: '1px solid rgba(255,255,255,0.1)' }}>
                <th style={{ padding: '8px' }}>Type</th>
                <th style={{ padding: '8px' }}>Amount</th>
                <th style={{ padding: '8px' }}>Description</th>
                <th style={{ padding: '8px' }}>Timestamp</th>
              </tr>
            </thead>
            <tbody>
              {transactions.slice(0, 10).map(tx => (
                <tr key={tx.id} style={{ borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
                  <td style={{ padding: '8px' }}>
                    <span className={`badge ${tx.amount > 0 ? 'badge-success' : 'badge-warning'}`}>
                      {tx.type}
                    </span>
                  </td>
                  <td style={{ padding: '8px', fontWeight: 600, color: tx.amount > 0 ? '#10b981' : '#f59e0b' }}>
                    {tx.amount > 0 ? `+${tx.amount}` : tx.amount} 🪙
                  </td>
                  <td style={{ padding: '8px' }}>{tx.description}</td>
                  <td style={{ padding: '8px', color: '#94a3b8' }}>
                    {new Date(tx.createdAt).toLocaleString()}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
};
