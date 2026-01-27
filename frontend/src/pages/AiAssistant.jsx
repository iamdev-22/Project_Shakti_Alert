import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';

export default function AiAssistant() {
    const [messages, setMessages] = useState([
        { type: 'ai', text: "👋 Hi! I'm Shakti, your safety companion! Ask me anything about the app, self-defense, or emergencies. What would you like to know?" }
    ]);
    const [userInput, setUserInput] = useState('');
    const [loading, setLoading] = useState(false);
    const messagesEndRef = useRef(null);
    const navigate = useNavigate();
    const BACKEND_URL = "http://127.0.0.1:5000";

    useEffect(() => {
        scrollToBottom();
    }, [messages]);

    const scrollToBottom = () => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    };

    const sendMessage = async () => {
        if (!userInput.trim()) return;

        // Add user message
        const userMessage = userInput;
        setMessages(prev => [...prev, { type: 'user', text: userMessage }]);
        setUserInput('');
        setLoading(true);

        try {
            const response = await fetch(`${BACKEND_URL}/api/ai/chat`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ message: userMessage })
            });

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}`);
            }

            const data = await response.json();
            setMessages(prev => [...prev, { type: 'ai', text: data.response || 'No response' }]);
        } catch (error) {
            console.error('AI Chat Error:', error);
            setMessages(prev => [...prev, { 
                type: 'ai', 
                text: `❌ Sorry, I'm having trouble connecting. Error: ${error.message}\n\n💡 **Try asking:**\n- "How does Shakti work?"\n- "Self-defense tips"\n- "Emergency contacts"`
            }]);
        } finally {
            setLoading(false);
        }
    };

    const handleKeyPress = (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    };

    return (
        <div className="min-h-screen bg-gradient-to-br from-purple-900 via-gray-900 to-black text-white p-4">
            <div className="max-w-2xl mx-auto h-screen flex flex-col">
                {/* Header */}
                <div className="flex items-center justify-between mb-4 pt-4">
                    <div className="flex items-center gap-3">
                        <div className="text-4xl">🤖</div>
                        <div>
                            <h1 className="text-2xl font-bold">Shakti AI Assistant</h1>
                            <p className="text-xs text-gray-400">Always here to help</p>
                        </div>
                    </div>
                    <button
                        onClick={() => navigate("/")}
                        className="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg transition"
                    >
                        ← Back
                    </button>
                </div>

                {/* Chat Messages */}
                <div className="flex-1 overflow-y-auto bg-gray-800/30 backdrop-blur-sm rounded-xl p-4 mb-4 space-y-4 border border-gray-700/50">
                    {messages.map((msg, idx) => (
                        <div
                            key={idx}
                            className={`flex ${msg.type === 'user' ? 'justify-end' : 'justify-start'}`}
                        >
                            <div
                                className={`max-w-xs lg:max-w-md xl:max-w-lg px-4 py-3 rounded-lg ${
                                    msg.type === 'user'
                                        ? 'bg-purple-600 text-white rounded-br-none'
                                        : 'bg-gray-700 text-gray-100 rounded-bl-none'
                                }`}
                            >
                                <p className="text-sm whitespace-pre-wrap">{msg.text}</p>
                            </div>
                        </div>
                    ))}
                    {loading && (
                        <div className="flex justify-start">
                            <div className="bg-gray-700 px-4 py-3 rounded-lg rounded-bl-none">
                                <div className="flex gap-2">
                                    <div className="w-2 h-2 bg-purple-400 rounded-full animate-bounce"></div>
                                    <div className="w-2 h-2 bg-purple-400 rounded-full animate-bounce" style={{ animationDelay: '0.1s' }}></div>
                                    <div className="w-2 h-2 bg-purple-400 rounded-full animate-bounce" style={{ animationDelay: '0.2s' }}></div>
                                </div>
                            </div>
                        </div>
                    )}
                    <div ref={messagesEndRef} />
                </div>

                {/* Suggested Questions */}
                {messages.length <= 1 && (
                    <div className="grid grid-cols-2 gap-2 mb-4">
                        <button
                            onClick={() => setUserInput("How does Shakti Alert work?")}
                            className="text-xs bg-gray-700 hover:bg-gray-600 p-2 rounded transition"
                        >
                            📱 How it works?
                        </button>
                        <button
                            onClick={() => setUserInput("Give me self-defense tips")}
                            className="text-xs bg-gray-700 hover:bg-gray-600 p-2 rounded transition"
                        >
                            🥋 Self-defense tips
                        </button>
                        <button
                            onClick={() => setUserInput("What to do in emergency?")}
                            className="text-xs bg-gray-700 hover:bg-gray-600 p-2 rounded transition"
                        >
                            🆘 Emergency help
                        </button>
                        <button
                            onClick={() => setUserInput("How to protect myself?")}
                            className="text-xs bg-gray-700 hover:bg-gray-600 p-2 rounded transition"
                        >
                            🛡️ Stay safe
                        </button>
                    </div>
                )}

                {/* Input Area */}
                <div className="flex gap-2">
                    <input
                        type="text"
                        value={userInput}
                        onChange={(e) => setUserInput(e.target.value)}
                        onKeyPress={handleKeyPress}
                        placeholder="Ask me anything..."
                        disabled={loading}
                        className="flex-1 px-4 py-3 rounded-lg bg-gray-700 border border-gray-600 focus:outline-none focus:border-purple-500 transition disabled:opacity-50"
                    />
                    <button
                        onClick={sendMessage}
                        disabled={loading || !userInput.trim()}
                        className="px-6 py-3 bg-purple-600 hover:bg-purple-700 disabled:bg-gray-600 rounded-lg font-semibold transition"
                    >
                        {loading ? '⏳' : '📤'}
                    </button>
                </div>

                {/* Info */}
                <p className="text-center text-xs text-gray-500 mt-3">
                    I'm powered by Google Gemini AI | Your questions help me improve
                </p>
            </div>
        </div>
    );
}
