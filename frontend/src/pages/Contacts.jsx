import React, { useState, useEffect } from 'react';

export default function Contacts() {
  const [contacts, setContacts] = useState([]);
  const [newName, setNewName] = useState('');
  const [newPhone, setNewPhone] = useState('');
  const [status, setStatus] = useState('');
  const BACKEND_URL = "http://127.0.0.1:5000";

  useEffect(() => {
    fetchContacts();
  }, []);

  const fetchContacts = async () => {
    try {
      const res = await fetch(`${BACKEND_URL}/contacts`);
      const data = await res.json();
      setContacts(data || []);
    } catch (err) {
      console.error("Failed to fetch contacts", err);
    }
  };

  const addContact = async () => {
    if (!newName || !newPhone) return;
    const updated = [...contacts, { name: newName, phone: newPhone }];
    
    try {
      const res = await fetch(`${BACKEND_URL}/contacts`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(updated)
      });
      if (res.ok) {
        setContacts(updated);
        setNewName('');
        setNewPhone('');
        setStatus('Contact saved!');
        setTimeout(() => setStatus(''), 3000);
      } else {
        setStatus('Failed to save.');
      }
    } catch (err) {
      setStatus('Error saving contact.');
    }
  };

  const removeContact = async (index) => {
    const updated = contacts.filter((_, i) => i !== index);
    try {
      const res = await fetch(`${BACKEND_URL}/contacts`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(updated)
      });
      if (res.ok) {
        setContacts(updated);
      }
    } catch (err) {
      console.error("Failed to remove contact", err);
    }
  };

  return (
    <div className="min-h-screen bg-gray-900 text-white p-10 flex flex-col items-center">
      <h1 className="text-4xl font-bold mb-8 text-red-500">Emergency Contacts</h1>
      
      <div className="w-full max-w-md bg-gray-800 p-6 rounded-lg shadow-lg">
        <div className="mb-4">
          <label className="block text-sm font-bold mb-2">Name</label>
          <input 
            className="w-full p-2 rounded bg-gray-700 border border-gray-600 focus:outline-none focus:border-red-500"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            placeholder="Guardian Name"
          />
        </div>
        <div className="mb-4">
          <label className="block text-sm font-bold mb-2">Phone (with country code)</label>
          <input 
            className="w-full p-2 rounded bg-gray-700 border border-gray-600 focus:outline-none focus:border-red-500"
            value={newPhone}
            onChange={(e) => setNewPhone(e.target.value)}
            placeholder="+919876543210"
          />
        </div>
        <button 
          onClick={addContact}
          className="w-full bg-red-600 hover:bg-red-700 text-white font-bold py-2 px-4 rounded transition"
        >
          Add Contact
        </button>
        {status && <p className="mt-2 text-center text-green-400">{status}</p>}
      </div>

      <div className="w-full max-w-md mt-8">
        <h2 className="text-2xl font-semibold mb-4">Your Guardians</h2>
        {contacts.length === 0 ? (
          <p className="text-gray-400">No contacts added yet.</p>
        ) : (
          <ul className="space-y-3">
            {contacts.map((c, i) => (
              <li key={i} className="bg-gray-800 p-4 rounded flex justify-between items-center">
                <div>
                  <p className="font-bold">{c.name}</p>
                  <p className="text-sm text-gray-400">{c.phone}</p>
                </div>
                <button 
                  onClick={() => removeContact(i)}
                  className="text-red-400 hover:text-red-200"
                >
                  Remove
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
