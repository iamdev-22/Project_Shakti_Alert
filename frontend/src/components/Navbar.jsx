import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import shieldIcon from "../components/shield.png";

export default function Navbar() {
  const [showMenu, setShowMenu] = useState(false);

  const navItems = [
    { to: "/", label: "Home", icon: <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"></path> },
    { to: "/track", label: "Live Track", icon: <path d="M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm0 18a8 8 0 1 1 8-8 8 8 0 0 1-8 8z M12 6a6 6 0 1 0 6 6 6 6 0 0 0-6-6z M12 8a4 4 0 1 1 4 4 4 4 0 0 1-4-4z"></path> },
    { to: "/history", label: "History", icon: <path d="M12 8v4l3 3m6-3a9 9 0 1 1-9-9 9 9 0 0 1 9 9z"></path> },
    { to: "/about", label: "About", icon: <path d="M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm0 18a8 8 0 1 1 8-8 8 8 0 0 1-8 8z M12 7h.01 M11 11h2v6h-2z"></path> },
  ];

  const menuItems = [
    { to: "/login", label: "Login", icon: "🔐" },
    { to: "/profile", label: "Profile", icon: "👤" },
    { to: "/friends", label: "Friends", icon: "👥" },
    { to: "/contacts", label: "Contacts", icon: "📞" },
    { to: "/ai-assistant", label: "AI Assistant", icon: "🤖" },
    { to: "/voice-enroll", label: "Voice ID", icon: "🎤" },
    { to: "/whatsapp-login", label: "WhatsApp", icon: "💬" },
  ];

  return (
    <nav className="flex items-center justify-between p-4 bg-transparent sticky top-0 z-50">
      <div className="flex items-center gap-3">
        <Link to="/" className="flex items-center gap-2 group">
          <img
            src={shieldIcon}
            alt="Shakti Shield"
            className="w-10 h-10 object-contain group-hover:scale-110 transition-transform duration-300"
          />
          <h1 className="text-2xl font-bold bg-gradient-to-r from-white  to-white bg-clip-text text-transparent hidden sm:block">
            Shakti Alert
          </h1>
        </Link>
      </div>

      <div className="flex items-center gap-2 sm:gap-4">
        {navItems.map((item) => (
          <Link
            key={item.to}
            to={item.to}
            title={item.label}
            className="p-2 rounded-full transition-all duration-300 hover:bg-gray-800 hover:text-pink-400 text-gray-300"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              width="24"
              height="24"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              className="w-6 h-6"
            >
              {item.icon}
            </svg>
          </Link>
        ))}

        {/* Menu Dropdown */}
        <div className="relative">
          <button
            onClick={() => setShowMenu(!showMenu)}
            className="p-2 rounded-full transition-all duration-300 hover:bg-gray-800 hover:text-pink-400 text-gray-300"
            title="More"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              width="24"
              height="24"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              className="w-6 h-6"
            >
              <circle cx="12" cy="12" r="1"></circle>
              <circle cx="12" cy="5" r="1"></circle>
              <circle cx="12" cy="19" r="1"></circle>
            </svg>
          </button>

          {showMenu && (
            <div className="absolute right-0 mt-2 w-48 bg-gray-900 border border-gray-700 rounded-lg shadow-xl overflow-hidden">
              {menuItems.map((item) => (
                <Link
                  key={item.to}
                  to={item.to}
                  onClick={() => setShowMenu(false)}
                  className="flex items-center gap-3 px-4 py-3 hover:bg-gray-800 transition-colors text-gray-300 hover:text-white"
                >
                  <span className="text-xl">{item.icon}</span>
                  <span>{item.label}</span>
                </Link>
              ))}
            </div>
          )}
        </div>
      </div>
    </nav>
  );
}
