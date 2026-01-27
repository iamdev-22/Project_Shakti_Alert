import React, { useState } from "react";
import { useNavigate } from "react-router-dom";

export default function Login() {
    const [isLogin, setIsLogin] = useState(true);
    const [formData, setFormData] = useState({
        email: "",
        password: "",
        name: "",
        last_name: ""
    });
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);
    const navigate = useNavigate();

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError("");
        setLoading(true);

        const endpoint = isLogin ? "/auth/login" : "/auth/signup";
        const payload = isLogin
            ? { email: formData.email, password: formData.password }
            : formData;

        try {
            const res = await fetch(`http://127.0.0.1:5000${endpoint}`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload)
            });

            const data = await res.json();

            if (data.success) {
                // Store token in localStorage
                if (data.token) {
                    localStorage.setItem("shakti_token", data.token);
                    localStorage.setItem("shakti_user", JSON.stringify(data.user));
                }

                // Redirect to home
                navigate("/");
            } else {
                setError(data.error || "Authentication failed");
            }
        } catch (err) {
            setError("Server error. Please try again.");
        } finally {
            setLoading(false);
        }
    };

    const handleChange = (e) => {
        setFormData({
            ...formData,
            [e.target.name]: e.target.value
        });
    };

    return (
        <div className="min-h-screen bg-gradient-to-br from-gray-900 via-red-900 to-black flex items-center justify-center p-4">
            <div className="max-w-md w-full">
                {/* Logo/Title */}
                <div className="text-center mb-8">
                    <h1 className="text-5xl font-bold text-white mb-2">🛡️ Shakti Alert</h1>
                    <p className="text-gray-400">Your Personal Safety Network</p>
                </div>

                {/* Form Card */}
                <div className="bg-gray-800/70 backdrop-blur-sm rounded-2xl p-8 border border-gray-700">
                    {/* Toggle Login/Signup */}
                    <div className="flex gap-2 mb-6 bg-gray-900/50 rounded-lg p-1">
                        <button
                            onClick={() => setIsLogin(true)}
                            className={`flex-1 py-2 rounded-lg transition ${isLogin ? "bg-red-600 text-white" : "text-gray-400 hover:text-white"
                                }`}
                        >
                            Login
                        </button>
                        <button
                            onClick={() => setIsLogin(false)}
                            className={`flex-1 py-2 rounded-lg transition ${!isLogin ? "bg-red-600 text-white" : "text-gray-400 hover:text-white"
                                }`}
                        >
                            Sign Up
                        </button>
                    </div>

                    {/* Error Message */}
                    {error && (
                        <div className="mb-4 p-3 bg-red-500/20 border border-red-500 rounded-lg text-red-300 text-sm">
                            {error}
                        </div>
                    )}

                    {/* Form */}
                    <form onSubmit={handleSubmit} className="space-y-4">
                        {!isLogin && (
                            <>
                                <div>
                                    <label className="block text-gray-300 text-sm mb-2">First Name</label>
                                    <input
                                        type="text"
                                        name="name"
                                        value={formData.name}
                                        onChange={handleChange}
                                        className="w-full px-4 py-3 bg-gray-900/50 border border-gray-600 rounded-lg text-white focus:outline-none focus:border-red-500 transition"
                                        placeholder="John"
                                    />
                                </div>
                                <div>
                                    <label className="block text-gray-300 text-sm mb-2">Last Name</label>
                                    <input
                                        type="text"
                                        name="last_name"
                                        value={formData.last_name}
                                        onChange={handleChange}
                                        className="w-full px-4 py-3 bg-gray-900/50 border border-gray-600 rounded-lg text-white focus:outline-none focus:border-red-500 transition"
                                        placeholder="Doe"
                                    />
                                </div>
                            </>
                        )}

                        <div>
                            <label className="block text-gray-300 text-sm mb-2">Email</label>
                            <input
                                type="email"
                                name="email"
                                value={formData.email}
                                onChange={handleChange}
                                required
                                className="w-full px-4 py-3 bg-gray-900/50 border border-gray-600 rounded-lg text-white focus:outline-none focus:border-red-500 transition"
                                placeholder="you@example.com"
                            />
                        </div>

                        <div>
                            <label className="block text-gray-300 text-sm mb-2">Password</label>
                            <input
                                type="password"
                                name="password"
                                value={formData.password}
                                onChange={handleChange}
                                required
                                className="w-full px-4 py-3 bg-gray-900/50 border border-gray-600 rounded-lg text-white focus:outline-none focus:border-red-500 transition"
                                placeholder="••••••••"
                            />
                        </div>

                        <button
                            type="submit"
                            disabled={loading}
                            className="w-full py-3 bg-red-600 hover:bg-red-700 disabled:bg-gray-600 text-white font-semibold rounded-lg transition"
                        >
                            {loading ? "Please wait..." : (isLogin ? "Login" : "Create Account")}
                        </button>
                    </form>

                    {/* Footer */}
                    <div className="mt-6 text-center text-sm text-gray-400">
                        {isLogin ? (
                            <p>
                                Don't have an account?{" "}
                                <button
                                    onClick={() => setIsLogin(false)}
                                    className="text-red-400 hover:text-red-300 transition"
                                >
                                    Sign up
                                </button>
                            </p>
                        ) : (
                            <p>
                                Already have an account?{" "}
                                <button
                                    onClick={() => setIsLogin(true)}
                                    className="text-red-400 hover:text-red-300 transition"
                                >
                                    Login
                                </button>
                            </p>
                        )}
                    </div>
                </div>

                {/* Skip Login (for testing) */}
                <div className="mt-4 text-center">
                    <button
                        onClick={() => navigate("/")}
                        className="text-gray-500 hover:text-gray-400 text-sm transition"
                    >
                        Skip for now →
                    </button>
                </div>
            </div>
        </div>
    );
}
