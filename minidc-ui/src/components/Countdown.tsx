import React, { useEffect, useState } from 'react';

interface CountdownProps {
  endsAt?: string;
  onExpire?: () => void;
}

export const Countdown: React.FC<CountdownProps> = ({ endsAt, onExpire }) => {
  const [timeLeft, setTimeLeft] = useState<string>('');

  useEffect(() => {
    if (!endsAt) {
      setTimeLeft('Not started');
      return;
    }

    const updateTimer = () => {
      const diff = new Date(endsAt).getTime() - Date.now();
      if (diff <= 0) {
        setTimeLeft('Expired');
        if (onExpire) {
          onExpire();
        }
        return;
      }

      const hours = Math.floor(diff / (1000 * 60 * 60));
      const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
      const seconds = Math.floor((diff % (1000 * 60)) / 1000);

      const hStr = hours > 0 ? `${hours}h ` : '';
      const mStr = `${minutes}m `;
      const sStr = `${seconds}s`;
      setTimeLeft(`${hStr}${mStr}${sStr}`);
    };

    updateTimer();
    const interval = setInterval(updateTimer, 1000);
    return () => clearInterval(interval);
  }, [endsAt, onExpire]);

  return <span className="countdown-pill">{timeLeft}</span>;
};

