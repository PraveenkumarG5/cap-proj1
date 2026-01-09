import React, { useEffect, useState } from 'react';
import axios from 'axios';

interface JobInstance {
  jobName: string;
  instanceId: number;
  status?: string;
  startTime?: string;
  endTime?: string;
  recordsProcessed?: number;
}

export const App: React.FC = () => {
  const [instances, setInstances] = useState<JobInstance[]>([]);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const refresh = async () => {
    const [instRes] = await Promise.all([
      axios.get<JobInstance[]>('/api/dashboard/instances')
    ]);
    setInstances(instRes.data.sort((a, b) => a.instanceId - b.instanceId));
  };

  useEffect(() => {
    refresh().catch(console.error);
  }, []);

  const trigger = async (type: 'daily' | 'interest' | 'file') => {
    try {
      setLoading(true);
      setMessage(null);
      if (type === 'daily') {
        await axios.post('/api/jobs/daily');
        setMessage('Daily transaction job triggered');
      } else if (type === 'interest') {
        await axios.post('/api/jobs/interest');
        setMessage('Monthly interest job triggered');
      } else {
        await axios.post('/api/jobs/load-file', null, {
          params: { path: 'backend/inbound/transactions.csv' }
        });
        setMessage('File load job triggered');
      }
      await refresh();
    } catch (e: any) {
      setMessage(e?.message ?? 'Error triggering job');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ fontFamily: 'system-ui, sans-serif', padding: '2rem', maxWidth: 1200, margin: '0 auto' }}>
      <h1>Enterprise Banking Batch Engine</h1>
      <p style={{ color: '#555' }}>Trigger batch jobs and view recent executions.</p>

      <section style={{ marginTop: '1.5rem', marginBottom: '1.5rem' }}>
        <h2>Job Triggers</h2>
        <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap' }}>
          <button onClick={() => trigger('daily')} disabled={loading}>
            Run Daily Transactions
          </button>
          <button onClick={() => trigger('interest')} disabled={loading}>
            Run Monthly Interest
          </button>
          <button onClick={() => trigger('file')} disabled={loading}>
            Load Transactions from CSV
          </button>
          <button onClick={() => refresh()} disabled={loading}>
            Refresh Dashboard
          </button>
        </div>
        {message && <p style={{ marginTop: '0.75rem' }}>{message}</p>}
      </section>

      <section style={{ marginBottom: '1.5rem' }}>
        <h2>Job Instances (latest)</h2>
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead>
            <tr>
              <th style={{ borderBottom: '1px solid #ddd', textAlign: 'left' }}>Job Name</th>
              <th style={{ borderBottom: '1px solid #ddd', textAlign: 'left' }}>Instance ID</th>
              <th style={{ borderBottom: '1px solid #ddd', textAlign: 'left' }}>Status</th>
              <th style={{ borderBottom: '1px solid #ddd', textAlign: 'left' }}>Start</th>
              <th style={{ borderBottom: '1px solid #ddd', textAlign: 'left' }}>End</th>
              <th style={{ borderBottom: '1px solid #ddd', textAlign: 'left' }}>Records Processed</th>
            </tr>
          </thead>
          <tbody>
            {instances.map((i) => (
              <tr key={`${i.jobName}-${i.instanceId}`}>
                <td style={{ borderBottom: '1px solid #f0f0f0', padding: '0.25rem 0.5rem' }}>{i.jobName}</td>
                <td style={{ borderBottom: '1px solid #f0f0f0', padding: '0.25rem 0.5rem' }}>{i.instanceId}</td>
                <td style={{ borderBottom: '1px solid #f0f0f0', padding: '0.25rem 0.5rem' }}>{i.status}</td>
                <td style={{ borderBottom: '1px solid #f0f0f0', padding: '0.25rem 0.5rem' }}>{i.startTime}</td>
                <td style={{ borderBottom: '1px solid #f0f0f0', padding: '0.25rem 0.5rem' }}>{i.endTime}</td>
                <td style={{ borderBottom: '1px solid #f0f0f0', padding: '0.25rem 0.5rem' }}>{i.recordsProcessed ?? 0}</td>
              </tr>
            ))}
            {instances.length === 0 && (
              <tr>
                <td colSpan={6} style={{ padding: '0.5rem' }}>
                  No job instances yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </section>
    </div>
  );
};


