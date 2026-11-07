import React, { useState } from 'react';
import { Node, OfferRequest } from '../api/types';

interface OfferFormProps {
  nodes: Node[];
  onSubmit: (data: OfferRequest) => Promise<void>;
  onCancel?: () => void;
}

export const OfferForm: React.FC<OfferFormProps> = ({ nodes, onSubmit, onCancel }) => {
  const [nodeId, setNodeId] = useState<string>(nodes[0]?.id || '');
  const [maxRamMb, setMaxRamMb] = useState<number>(4096);
  const [maxCores, setMaxCores] = useState<number>(2);
  const [maxDiskGb, setMaxDiskGb] = useState<number>(20);
  const [maxHours, setMaxHours] = useState<number>(4);
  const [autoApprove, setAutoApprove] = useState<boolean>(false);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!nodeId) {
      setError('Please select a node');
      return;
    }

    setIsSubmitting(true);
    setError(null);

    try {
      await onSubmit({
        nodeId,
        maxRamMb,
        maxCores,
        maxDiskGb,
        maxHours,
        autoApprove,
      });
    } catch (err: any) {
      setError(err.message || 'Failed to submit offer');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <form className="offer-form card" onSubmit={handleSubmit}>
      <h3>Publish Capacity Offer</h3>

      {error && <div className="alert alert-error">{error}</div>}

      <div className="form-group">
        <label>Select Node:</label>
        <select value={nodeId} onChange={(e) => setNodeId(e.target.value)} required>
          <option value="" disabled>-- Select compute node --</option>
          {nodes.map((n) => (
            <option key={n.id} value={n.id}>
              {n.name} ({n.status})
            </option>
          ))}
        </select>
      </div>

      <div className="form-group">
        <label>Max RAM: {maxRamMb} MB ({(maxRamMb / 1024).toFixed(1)} GB)</label>
        <input
          type="range"
          min={512}
          max={32768}
          step={512}
          value={maxRamMb}
          onChange={(e) => setMaxRamMb(Number(e.target.value))}
        />
      </div>

      <div className="form-group">
        <label>Max CPU Cores: {maxCores}</label>
        <input
          type="range"
          min={1}
          max={16}
          step={1}
          value={maxCores}
          onChange={(e) => setMaxCores(Number(e.target.value))}
        />
      </div>

      <div className="form-group">
        <label>Max Disk: {maxDiskGb} GB</label>
        <input
          type="range"
          min={5}
          max={200}
          step={5}
          value={maxDiskGb}
          onChange={(e) => setMaxDiskGb(Number(e.target.value))}
        />
      </div>

      <div className="form-group">
        <label>Max Lease Duration: {maxHours} Hours</label>
        <input
          type="range"
          min={1}
          max={48}
          step={1}
          value={maxHours}
          onChange={(e) => setMaxHours(Number(e.target.value))}
        />
      </div>

      <div className="form-group checkbox-group">
        <label>
          <input
            type="checkbox"
            checked={autoApprove}
            onChange={(e) => setAutoApprove(e.target.checked)}
          />
          Auto-approve borrower requests
        </label>
      </div>

      <div className="form-actions">
        <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
          {isSubmitting ? 'Publishing...' : 'Publish Offer'}
        </button>
        {onCancel && (
          <button type="button" className="btn btn-secondary" onClick={onCancel}>
            Cancel
          </button>
        )}
      </div>
    </form>
  );
};

