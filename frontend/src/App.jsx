import React from 'react';
import Navbar from './components/Navbar';
import Footer from './components/Footer';
import Home from './pages/Home';
import Track from './pages/Track';
import History from './pages/History';
import About from './pages/About';
import Contacts from './pages/Contacts';
import VoiceEnrollment from './pages/VoiceEnrollment';
import WhatsAppLogin from './pages/WhatsAppLogin';
import Login from './pages/Login';
import Profile from './pages/Profile';
import Friends from './pages/Friends';
import AiAssistant from './pages/AiAssistant';

import { BrowserRouter, Routes, Route } from 'react-router-dom';

// Error Boundary Component
class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, errorInfo) {
    console.error("Error caught by boundary:", error, errorInfo);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen bg-gray-900 text-white flex flex-col items-center justify-center p-4">
          <div className="text-center max-w-md">
            <div className="text-6xl mb-4">⚠️</div>
            <h1 className="text-3xl font-bold mb-2">Oops! Something went wrong</h1>
            <p className="text-gray-400 mb-6">{this.state.error?.message || "An unexpected error occurred"}</p>
            <button
              onClick={() => {
                this.setState({ hasError: false, error: null });
                window.location.href = '/';
              }}
              className="px-6 py-3 bg-red-600 hover:bg-red-700 rounded-lg font-semibold transition"
            >
              🏠 Go Back Home
            </button>
            <p className="text-sm text-gray-500 mt-4">
              If this keeps happening, try refreshing the page
            </p>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}

export default function App() {
  return (
    <ErrorBoundary>
      <BrowserRouter>
        <Navbar />
        <div className="container">
          <Routes>
            <Route path="/" element={<Home />} />
            <Route path="/login" element={<Login />} />
            <Route path="/profile" element={<Profile />} />
            <Route path="/friends" element={<Friends />} />
            <Route path="/track" element={<Track />} />
            <Route path="/contacts" element={<Contacts />} />
            <Route path="/ai-assistant" element={<AiAssistant />} />
            <Route path="/voice-enroll" element={<VoiceEnrollment />} />
            <Route path="/whatsapp-login" element={<WhatsAppLogin />} />
            <Route path="/history" element={<History />} />
            <Route path="/about" element={<About />} />
            
            {/* Fallback for 404 */}
            <Route path="*" element={
              <div className="min-h-screen bg-gray-900 text-white flex flex-col items-center justify-center">
                <h1 className="text-4xl font-bold mb-4">404 - Page Not Found</h1>
                <a href="/" className="text-purple-400 hover:text-purple-300">← Go Back Home</a>
              </div>
            } />
          </Routes>
        </div>
        <Footer />
      </BrowserRouter>
    </ErrorBoundary>
  );
}
