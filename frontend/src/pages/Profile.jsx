import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";

export default function Profile() {
    const [profile, setProfile] = useState(null);
    const [editing, setEditing] = useState(false);
    const [formData, setFormData] = useState({});
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const navigate = useNavigate();

    useEffect(() => {
        fetchProfile();
    }, []);

    const fetchProfile = async () => {
        const token = localStorage.getItem("shakti_token");

        if (!token) {
            navigate("/login");
            return;
        }

        try {
            const res = await fetch("http://127.0.0.1:5000/profile", {
                headers: {
                    "Authorization": `Bearer ${token}`
                }
            });

            const data = await res.json();

            if (data.success) {
                setProfile(data.profile);
                setFormData(data.profile);
            } else {
                // Token invalid, redirect to login
                localStorage.removeItem("shakti_token");
                navigate("/login");
            }
        } catch (err) {
            console.error("Failed to fetch profile:", err);
        } finally {
            setLoading(false);
        }
    };

    const handleSave = async () => {
        const token = localStorage.getItem("shakti_token");
        setSaving(true);

        try {
            const res = await fetch("http://127.0.0.1:5000/profile", {
                method: "PUT",
                headers: {
                    "Content-Type": "application/json",
                    "Authorization": `Bearer ${token}`
                },
                body: JSON.stringify(formData)
            });

            const data = await res.json();

            if (data.success) {
                setProfile(formData);
                setEditing(false);
            }
        } catch (err) {
            console.error("Failed to update profile:", err);
        } finally {
            setSaving(false);
        }
    };

    const handleLogout = () => {
        localStorage.removeItem("shakti_token");
        localStorage.removeItem("shakti_user");
        navigate("/login");
    };

    const handleChange = (e) => {
        setFormData({
            ...formData,
            [e.target.name]: e.target.value
        });
    };

    if (loading) {
        return (
            <div className="min-h-screen bg-gradient-to-br from-gray-900 via-red-900 to-black flex items-center justify-center">
                <div className="text-white text-xl">Loading profile...</div>
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-gradient-to-br from-gray-900 via-red-900 to-black text-white p-8">
            <div className="max-w-4xl mx-auto">
                {/* Header */}
                <div className="flex justify-between items-center mb-8">
                    <h1 className="text-4xl font-bold">👤 My Profile</h1>
                    <div className="flex gap-3">
                        <button
                            onClick={() => navigate("/")}
                            className="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg transition"
                        >
                            ← Back
                        </button>
                        <button
                            onClick={handleLogout}
                            className="px-4 py-2 bg-red-600 hover:bg-red-700 rounded-lg transition"
                        >
                            Logout
                        </button>
                    </div>
                </div>

                {/* Profile Card */}
                <div className="bg-gray-800/70 backdrop-blur-sm rounded-2xl p-8 border border-gray-700">
                    {/* Profile Photo */}
                    <div className="flex justify-center mb-8">
                        <div className="relative">
                            <div className="w-32 h-32 bg-gradient-to-br from-red-500 to-purple-600 rounded-full flex items-center justify-center text-5xl">
                                {profile?.profile_photo || "👤"}
                            </div>
                            {editing && (
                                <button className="absolute bottom-0 right-0 bg-blue-600 hover:bg-blue-700 rounded-full p-2 transition">
                                    📷
                                </button>
                            )}
                        </div>
                    </div>

                    {/* Profile Info */}
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                        {/* Email (Read-only) */}
                        <div>
                            <label className="block text-gray-400 text-sm mb-2">Email</label>
                            <div className="px-4 py-3 bg-gray-900/50 border border-gray-600 rounded-lg text-gray-300">
                                {profile?.email}
                            </div>
                        </div>

                        {/* First Name */}
                        <div>
                            <label className="block text-gray-400 text-sm mb-2">First Name</label>
                            {editing ? (
                                <input
                                    type="text"
                                    name="name"
                                    value={formData.name || ""}
                                    onChange={handleChange}
                                    className="w-full px-4 py-3 bg-gray-900/50 border border-gray-600 rounded-lg text-white focus:outline-none focus:border-red-500 transition"
                                />
                            ) : (
                                <div className="px-4 py-3 bg-gray-900/50 border border-gray-600 rounded-lg">
                                    {profile?.name || "Not set"}
                                </div>
                            )}
                        </div>

                        {/* Last Name */}
                        <div>
                            <label className="block text-gray-400 text-sm mb-2">Last Name</label>
                            {editing ? (
                                <input
                                    type="text"
                                    name="last_name"
                                    value={formData.last_name || ""}
                                    onChange={handleChange}
                                    className="w-full px-4 py-3 bg-gray-900/50 border border-gray-600 rounded-lg text-white focus:outline-none focus:border-red-500 transition"
                                />
                            ) : (
                                <div className="px-4 py-3 bg-gray-900/50 border border-gray-600 rounded-lg">
                                    {profile?.last_name || "Not set"}
                                </div>
                            )}
                        </div>

                        {/* Date of Birth */}
                        <div>
                            <label className="block text-gray-400 text-sm mb-2">Date of Birth</label>
                            {editing ? (
                                <input
                                    type="date"
                                    name="dob"
                                    value={formData.dob || ""}
                                    onChange={handleChange}
                                    className="w-full px-4 py-3 bg-gray-900/50 border border-gray-600 rounded-lg text-white focus:outline-none focus:border-red-500 transition"
                                />
                            ) : (
                                <div className="px-4 py-3 bg-gray-900/50 border border-gray-600 rounded-lg">
                                    {profile?.dob || "Not set"}
                                </div>
                            )}
                        </div>

                        {/* Age */}
                        <div>
                            <label className="block text-gray-400 text-sm mb-2">Age</label>
                            {editing ? (
                                <input
                                    type="number"
                                    name="age"
                                    value={formData.age || ""}
                                    onChange={handleChange}
                                    className="w-full px-4 py-3 bg-gray-900/50 border border-gray-600 rounded-lg text-white focus:outline-none focus:border-red-500 transition"
                                />
                            ) : (
                                <div className="px-4 py-3 bg-gray-900/50 border border-gray-600 rounded-lg">
                                    {profile?.age || "Not set"}
                                </div>
                            )}
                        </div>

                        {/* Address */}
                        <div className="md:col-span-2">
                            <label className="block text-gray-400 text-sm mb-2">Address</label>
                            {editing ? (
                                <textarea
                                    name="address"
                                    value={formData.address || ""}
                                    onChange={handleChange}
                                    rows="3"
                                    className="w-full px-4 py-3 bg-gray-900/50 border border-gray-600 rounded-lg text-white focus:outline-none focus:border-red-500 transition"
                                />
                            ) : (
                                <div className="px-4 py-3 bg-gray-900/50 border border-gray-600 rounded-lg">
                                    {profile?.address || "Not set"}
                                </div>
                            )}
                        </div>
                    </div>

                    {/* Action Buttons */}
                    <div className="mt-8 flex justify-end gap-3">
                        {editing ? (
                            <>
                                <button
                                    onClick={() => {
                                        setEditing(false);
                                        setFormData(profile);
                                    }}
                                    className="px-6 py-3 bg-gray-700 hover:bg-gray-600 rounded-lg transition"
                                >
                                    Cancel
                                </button>
                                <button
                                    onClick={handleSave}
                                    disabled={saving}
                                    className="px-6 py-3 bg-green-600 hover:bg-green-700 disabled:bg-gray-600 rounded-lg transition"
                                >
                                    {saving ? "Saving..." : "Save Changes"}
                                </button>
                            </>
                        ) : (
                            <button
                                onClick={() => setEditing(true)}
                                className="px-6 py-3 bg-blue-600 hover:bg-blue-700 rounded-lg transition"
                            >
                                Edit Profile
                            </button>
                        )}
                    </div>

                    {/* Account Info */}
                    <div className="mt-8 pt-6 border-t border-gray-700 text-sm text-gray-400">
                        <p>Account created: {profile?.created_at ? new Date(profile.created_at).toLocaleDateString() : "Unknown"}</p>
                    </div>
                </div>
            </div>
        </div>
    );
}
