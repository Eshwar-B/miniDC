import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { CreateLeaseRequest, Offer } from '../api/types';

export const Marketplace: React.FC = () => {
  const [offers, setOffers] = useState<Offer[]>([]);
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

  useEffect(() => {
    fetchOffers();
    // Poll available offers
    // TODO(phase-5): WebSocket update on offer availability
    const interval = setInterval(fetchOffers, 10000);
    return () => clearInterval(interval);
  }, []);

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

