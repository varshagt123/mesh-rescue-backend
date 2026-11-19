import { useState, useEffect } from 'react';
import { API, socket } from '../services/api';

function CommunicationPanel({ currentUser }) {
  const [messages, setMessages] = useState([]);
  const [recipient, setRecipient] = useState('rescue_room');
  const [text, setText] = useState('');
  const [messageType, setMessageType] = useState('CHAT');

  useEffect(() => {
    async function loadMessages() {
      try {
        const res = await API.get('/messages');
        setMessages(res.data);
      } catch (err) {
        console.error('Failed to load messages:', err);
      }
    }
    loadMessages();

    const handleNewMsg = (msg) => {
      setMessages((prev) => (prev.some((m) => m.id === msg.id) ? prev : [...prev, msg]));
    };

    const handleDelivered = ({ id }) => {
      setMessages((prev) =>
        prev.map((m) => (m.id === id ? { ...m, delivery_status: 'DELIVERED' } : m))
      );
    };

    socket.on('receive_message', handleNewMsg);
    socket.on('message_delivered', handleDelivered);

    return () => {
      socket.off('receive_message', handleNewMsg);
      socket.off('message_delivered', handleDelivered);
    };
  }, []);

  const handleSend = (e) => {
    e.preventDefault();
    if (!text.trim()) return;

    const payload = {
      client_msg_id: `MSG-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
      sender: currentUser.username,
      recipient: recipient.trim() || 'rescue_room',
      content: text.trim(),
      message_type: messageType,
      is_mesh: false
    };

    socket.emit('send_message', payload);
    setText('');
  };

  return (
    <div style={{ padding: '1rem' }}>
      <h2>Emergency Communication</h2>
      <p style={{ color: '#64748b', marginBottom: '1rem' }}>
        Synced messages from victims & field nodes — send responses or rescue instructions
      </p>

      <div style={{
        height: '380px', overflowY: 'auto', border: '1px solid #cbd5e1',
        borderRadius: '8px', padding: '1rem', backgroundColor: '#f8fafc', marginBottom: '1rem'
      }}>
        {messages.length === 0 ? (
          <p style={{ color: '#94a3b8' }}>No synchronized messages yet.</p>
        ) : (
          messages.map((msg) => (
            <div key={msg.id} style={{
              marginBottom: '0.75rem', padding: '0.75rem', borderRadius: '8px',
              backgroundColor: msg.sender === currentUser.username ? '#dbeafe' : '#ffffff',
              border: '1px solid #e2e8f0'
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.8rem', color: '#475569' }}>
                <span>
                  <strong>{msg.sender}</strong> → <em>{msg.recipient || 'All Rescue'}</em>
                  {msg.message_type === 'RESCUE_INSTRUCTION' && (
                    <span style={{ marginLeft: '8px', background: '#fef08a', padding: '2px 6px', borderRadius: '4px', fontWeight: 'bold' }}>
                      RESCUE INSTRUCTION
                    </span>
                  )}
                  {msg.message_type === 'MEDICAL_REQUEST' && (
                    <span style={{ marginLeft: '8px', background: '#fecaca', color: '#991b1b', padding: '2px 6px', borderRadius: '4px', fontWeight: 'bold' }}>
                      MEDICAL ASSISTANCE
                    </span>
                  )}
                </span>
                <span>
                  {new Date(msg.timestamp).toLocaleString()} • <strong>{msg.delivery_status || 'DELIVERED'}</strong>
                </span>
              </div>
              <p style={{ margin: '0.4rem 0 0', color: '#0f172a' }}>{msg.content}</p>
            </div>
          ))
        )}
      </div>

      <form onSubmit={handleSend} style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
        <input
          type="text"
          value={recipient}
          onChange={(e) => setRecipient(e.target.value)}
          placeholder="Victim username (or rescue_room)"
          style={{ padding: '0.6rem', borderRadius: '6px', border: '1px solid #cbd5e1', width: '200px' }}
        />
        <select
          value={messageType}
          onChange={(e) => setMessageType(e.target.value)}
          style={{ padding: '0.6rem', borderRadius: '6px', border: '1px solid #cbd5e1' }}
        >
          <option value="CHAT">Direct Response</option>
          <option value="RESCUE_INSTRUCTION">Rescue Instruction</option>
        </select>
        <input
          type="text"
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="Type response or rescue instruction..."
          style={{ flex: 1, minWidth: '220px', padding: '0.6rem', borderRadius: '6px', border: '1px solid #cbd5e1' }}
        />
        <button
          type="submit"
          style={{
            padding: '0.6rem 1.2rem', borderRadius: '6px', border: 'none',
            backgroundColor: '#2563eb', color: 'white', fontWeight: 'bold', cursor: 'pointer'
          }}
        >
          Send
        </button>
      </form>
    </div>
  );
}

export default CommunicationPanel;