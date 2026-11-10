import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { CreateLeaseRequest, Offer, ScoredOfferRecommendation } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { stompManager } from '../ws/stompClient';

export const Marketplace: React.FC = () => {
  const { token } = useAuth();
  const [offers, setOffers] = useState<Offer[]>([]);
  const [recommendations, setRecommendations] = useState<ScoredOfferRecommendation[]>([]);
  const [showAiMatchmaker, setShowAiMatchmaker] = useState<boolean>(false);
  const [selectedOffer, setSelectedOffer] = useState<Offer | null>(null);
  const [ramMb, setRamMb] = useState<number>(2048);
  const [cores, setCores] = useState<number>(2);
  const [diskGb, setDiskGb] = useState<number>(20);
  const [hours, setHours] = useState<number>(2);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const navigate = useNavigate();

  const fetchOffers = async () => {
    try {
      const available = await api.getAvailableOffers();
      setOffers(available);
    } catch (err: any) {
      setError(err.message || 'Failed to load offers');
    } finally {
      setLoading(false);
    }
  };

  const handleFetchAiRecommendations = async () => {
    try {
      const recs = await api.recommendOffers({ ramMb, cores, diskGb, hours });
      setRecommendations(recs);
    } catch (err: any) {
      console.warn('Failed to fetch AI recommendations', err);
    }
  };

  useEffect(() => {
    fetchOffers();

    let unsubscribe: (() => void) | undefined;
    if (token) {
      stompManager.initialize(token);
      unsubscribe = stompManager.subscribe('/topic/offers', () => {
        fetchOffers();
      });
    }

    return () => {
      if (unsubscribe) {
        unsubscribe();
      }
    };
  }, [token]);

  const handleSelectOffer = (offer: Offer) => {
    setSelectedOffer(offer);
    setRamMb(Math.min(2048, offer.maxRamMb));
    setCores(Math.min(2, offer.maxCores));
    setDiskGb(Math.min(20, offer.maxDiskGb));
    setHours(Math.min(2, offer.maxHours));
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const handleCreateLease = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedOffer) return;

    setSubmitting(true);
    setError(null);

    const payload: CreateLeaseRequest = {
      offerId: selectedOffer.id,
      ramMb,
      cores,
      diskGb,
      requestedHours: hours,
    };

    try {
      await api.createLease(payload);
      navigate('/leases');
    } catch (err: any) {
      setError(err.message || 'Failed creating lease request');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="marketplace-container">
      <h2>Compute Marketplace</h2>
      <p className="subtitle">Discover idle machines, reserve RAM & CPU cores, and launch disposable Linux desktop sandboxes</p>

      {error && <div className="alert alert-error">{error}</div>}

      <div style={{ marginBottom: '1.5rem', display: 'flex', gap: '1rem', alignItems: 'center', background: '#111827', padding: '0.75rem 1rem', borderRadius: '8px', border: '1px solid #1f2937' }}>
        <span style={{ fontSize: '1.2rem' }}>🤖</span>
        <div style={{ flex: 1 }}>
          <strong style={{ color: '#38bdf8' }}>AI Intelligent Matchmaker</strong>
          <p style={{ margin: 0, fontSize: '0.85rem', color: '#9ca3af' }}>Let the platform find and rank the most reliable host nodes based on hardware headroom and historical uptime.</p>
        </div>
        <button
          className="btn btn-secondary btn-sm"
          onClick={() => {
            const next = !showAiMatchmaker;
            setShowAiMatchmaker(next);
            if (next) handleFetchAiRecommendations();
          }}
        >
          {showAiMatchmaker ? 'Hide AI Suggestions' : 'Get Recommendations'}
        </button>
      </div>

      {showAiMatchmaker && recommendations.length > 0 && (
        <div style={{ marginBottom: '2rem', padding: '1rem', background: '#0f172a', borderRadius: '8px', border: '1px solid #0284c7' }}>
          <h3 style={{ margin: '0 0 1rem 0', color: '#38bdf8' }}>⭐ Top AI Recommended Offers</h3>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '1rem' }}>
            {recommendations.slice(0, 3).map((rec) => (
              <div key={rec.offer.id} style={{ background: '#1e293b', padding: '1rem', borderRadius: '6px', border: '1px solid #334155' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ fontWeight: 'bold', color: '#f8fafc' }}>{rec.node.name}</span>
                  <span style={{ background: '#0284c7', color: '#fff', padding: '0.2rem 0.5rem', borderRadius: '4px', fontSize: '0.75rem' }}>
                    Score: {rec.matchScore.score}%
                  </span>
                </div>
                <p style={{ fontSize: '0.8rem', color: '#94a3b8', margin: '0.5rem 0' }}>{rec.matchScore.recommendationReason}</p>
                <div style={{ fontSize: '0.8rem', color: '#cbd5e1' }}>
                  <div>RAM: {rec.offer.maxRamMb} MB | Cores: {rec.offer.maxCores}</div>
                  <div>Reliability: {rec.matchScore.reliabilityPercent}%</div>
                </div>
                <button
                  className="btn btn-primary btn-sm btn-block"
                  style={{ marginTop: '0.75rem' }}
                  onClick={() => handleSelectOffer(rec.offer)}
                >
                  Select This Best Match
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      {selectedOffer && (
        <div className="card request-lease-card">
          <h3>Request Lease on Node</h3>
          <p className="subtitle">Configure resource allocation within host-offered upper limits</p>

          <form onSubmit={handleCreateLease}>
            <div className="form-group">
              <label>RAM: {ramMb} MB (Max {selectedOffer.maxRamMb} MB)</label>
              <input
                type="range"
                min={512}
                max={selectedOffer.maxRamMb}
                step={512}
                value={ramMb}
                onChange={(e) => setRamMb(Number(e.target.value))}
              />
            </div>

            <div className="form-group">
              <label>CPU Cores: {cores} (Max {selectedOffer.maxCores})</label>
              <input
                type="range"
                min={1}
                max={selectedOffer.maxCores}
                step={1}
                value={cores}
                onChange={(e) => setCores(Number(e.target.value))}
              />
            </div>

            <div className="form-group">
              <label>Disk: {diskGb} GB (Max {selectedOffer.maxDiskGb} GB)</label>
              <input
                type="range"
                min={5}
                max={selectedOffer.maxDiskGb}
                step={5}
                value={diskGb}
                onChange={(e) => setDiskGb(Number(e.target.value))}
              />
            </div>

            <div className="form-group">
              <label>Duration: {hours} Hours (Max {selectedOffer.maxHours} Hours)</label>
              <input
                type="range"
                min={1}
                max={selectedOffer.maxHours}
                step={1}
                value={hours}
                onChange={(e) => setHours(Number(e.target.value))}
              />
            </div>

            <div className="form-actions">
              <button type="submit" className="btn btn-primary" disabled={submitting}>
                {submitting ? 'Submitting Request...' : selectedOffer.autoApprove ? 'Instant Provision' : 'Request Lease'}
              </button>
              <button type="button" className="btn btn-secondary" onClick={() => setSelectedOffer(null)}>
                Cancel
              </button>
            </div>
          </form>
        </div>
      )}

      {loading ? (
        <p>Loading available offers...</p>
      ) : offers.length === 0 ? (
        <div className="card text-center empty-card">
          <h3>No Capacity Available Right Now</h3>
          <p>Lenders haven't published offers on active online nodes. Check back soon or register your own machine.</p>
        </div>
      ) : (
        <div className="offers-grid">
          {offers.map((offer) => (
            <div key={offer.id} className="card offer-card">
              <div className="offer-header">
                <h4>Node Offer</h4>
                {offer.autoApprove && <span className="badge badge-success">Auto-Approve</span>}
              </div>

              <div className="offer-specs">
                <p><strong>Max RAM:</strong> {(offer.maxRamMb / 1024).toFixed(1)} GB ({offer.maxRamMb} MB)</p>
                <p><strong>Max CPU:</strong> {offer.maxCores} Cores</p>
                <p><strong>Max Disk:</strong> {offer.maxDiskGb} GB</p>
                <p><strong>Max Time:</strong> {offer.maxHours} Hours</p>
                {offer.windowStart && offer.windowEnd && (
                  <p><strong>Window:</strong> {offer.windowStart} - {offer.windowEnd}</p>
                )}
              </div>

              <button className="btn btn-primary btn-block" onClick={() => handleSelectOffer(offer)}>
                Select & Configure
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

