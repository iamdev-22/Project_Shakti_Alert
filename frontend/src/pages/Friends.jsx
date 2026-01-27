import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";

export default function Friends() {
    const [tab, setTab] = useState("circles"); // circles or friends
    const [circles, setCircles] = useState([]);
    const [friends, setFriends] = useState([]);
    const [showCreateCircle, setShowCreateCircle] = useState(false);
    const [showJoinCircle, setShowJoinCircle] = useState(false);
    const [showAddFriend, setShowAddFriend] = useState(false);
    const [newCircleName, setNewCircleName] = useState("");
    const [joinCode, setJoinCode] = useState("");
    const [friendCode, setFriendCode] = useState("");
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [success, setSuccess] = useState("");
    const navigate = useNavigate();
    const BACKEND_URL = "http://127.0.0.1:5000";

    useEffect(() => {
        fetchCircles();
        fetchFriends();
    }, []);

    const getAuthHeaders = () => {
        const token = localStorage.getItem("shakti_token");
        if (!token) {
            navigate("/login");
            return null;
        }
        return { 
            "Authorization": `Bearer ${token}`,
            "Content-Type": "application/json"
        };
    };

    const fetchCircles = async () => {
        const headers = getAuthHeaders();
        if (!headers) return;

        try {
            const res = await fetch(`${BACKEND_URL}/api/circles/list`, { headers });
            const data = await res.json();

            if (data.success) {
                setCircles(data.circles || []);
            }
        } catch (err) {
            console.error("Failed to fetch circles:", err);
        } finally {
            setLoading(false);
        }
    };

    const fetchFriends = async () => {
        const headers = getAuthHeaders();
        if (!headers) return;

        try {
            const res = await fetch(`${BACKEND_URL}/friends/list`, { headers });
            const data = await res.json();

            if (data.success) {
                setFriends(data.friends || []);
            }
        } catch (err) {
            console.error("Failed to fetch friends:", err);
        }
    };

    const createCircle = async () => {
        if (!newCircleName.trim()) {
            setError("Please enter a circle name");
            return;
        }

        const headers = getAuthHeaders();
        if (!headers) return;

        try {
            const res = await fetch(`${BACKEND_URL}/api/circles/create`, {
                method: "POST",
                headers,
                body: JSON.stringify({ name: newCircleName })
            });
            const data = await res.json();

            if (data.success) {
                setSuccess("Circle created successfully!");
                setNewCircleName("");
                setShowCreateCircle(false);
                fetchCircles();
                setTimeout(() => setSuccess(""), 3000);
            } else {
                setError(data.error || "Failed to create circle");
            }
        } catch (err) {
            setError("Error creating circle: " + err.message);
        }
    };

    const joinCircle = async () => {
        if (!joinCode.trim()) {
            setError("Please enter a circle code");
            return;
        }

        const headers = getAuthHeaders();
        if (!headers) return;

        try {
            const res = await fetch(`${BACKEND_URL}/api/circles/join`, {
                method: "POST",
                headers,
                body: JSON.stringify({ invite_code: joinCode.toUpperCase() })
            });
            const data = await res.json();

            if (data.success) {
                setSuccess("Joined circle successfully!");
                setJoinCode("");
                setShowJoinCircle(false);
                fetchCircles();
                setTimeout(() => setSuccess(""), 3000);
            } else {
                setError(data.error || "Failed to join circle");
            }
        } catch (err) {
            setError("Error joining circle: " + err.message);
        }
    };

    const deleteCircle = async (circleId, circleName) => {
        if (!window.confirm(`Delete circle "${circleName}"?`)) return;

        const headers = getAuthHeaders();
        if (!headers) return;

        try {
            const res = await fetch(`${BACKEND_URL}/api/circles/${circleId}/delete`, {
                method: "DELETE",
                headers
            });
            const data = await res.json();

            if (data.success) {
                setSuccess("Circle deleted");
                fetchCircles();
                setTimeout(() => setSuccess(""), 3000);
            } else {
                setError(data.error || "Failed to delete circle");
            }
        } catch (err) {
            setError("Error deleting circle: " + err.message);
        }
    };

    const removeFriend = async (friendId, friendName) => {
        if (!window.confirm(`Remove ${friendName} from friends?`)) return;

        const headers = getAuthHeaders();
        if (!headers) return;

        try {
            const res = await fetch(`${BACKEND_URL}/friends/remove/${friendId}`, {
                method: "DELETE",
                headers
            });
            const data = await res.json();

            if (data.success) {
                setSuccess(`Removed ${friendName}`);
                fetchFriends();
                setTimeout(() => setSuccess(""), 3000);
            }
        } catch (err) {
            setError("Failed to remove friend");
        }
    };

    const addFriend = async () => {
        if (!friendCode.trim()) {
            setError("Please enter a friend code");
            return;
        }

        const headers = getAuthHeaders();
        if (!headers) return;

        try {
            const res = await fetch(`${BACKEND_URL}/friends/add`, {
                method: "POST",
                headers,
                body: JSON.stringify({ code: friendCode.toUpperCase() })
            });
            const data = await res.json();

            if (data.success) {
                setSuccess(`Added ${data.friend?.name || 'friend'}`);
                setFriendCode("");
                setShowAddFriend(false);
                fetchFriends();
                setTimeout(() => setSuccess(""), 3000);
            } else {
                setError(data.error || "Failed to add friend");
            }
        } catch (err) {
            setError("Error adding friend: " + err.message);
        }
    };

    const copyCode = (code) => {
        navigator.clipboard.writeText(code);
        setSuccess("Code copied!");
        setTimeout(() => setSuccess(""), 2000);
    };

    return (
        <div className="min-h-screen bg-gradient-to-br from-gray-900 via-red-900 to-black text-white p-8">
            <div className="max-w-6xl mx-auto">
                {/* Header */}
                <div className="flex justify-between items-center mb-8">
                    <h1 className="text-4xl font-bold">👥 Family & Friends</h1>
                    <button
                        onClick={() => navigate("/")}
                        className="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg transition"
                    >
                        ← Back
                    </button>
                </div>

                {/* Messages */}
                {success && (
                    <div className="mb-4 p-4 bg-green-500/20 border border-green-500 rounded-lg text-green-300 flex justify-between">
                        <span>{success}</span>
                        <button onClick={() => setSuccess("")}>✕</button>
                    </div>
                )}
                {error && (
                    <div className="mb-4 p-4 bg-red-500/20 border border-red-500 rounded-lg text-red-300 flex justify-between">
                        <span>{error}</span>
                        <button onClick={() => setError("")}>✕</button>
                    </div>
                )}

                {/* Tabs */}
                <div className="flex gap-4 mb-8 border-b border-gray-700">
                    <button
                        onClick={() => setTab("circles")}
                        className={`pb-3 px-4 font-semibold transition-all ${
                            tab === "circles"
                                ? "border-b-2 border-red-500 text-red-400"
                                : "text-gray-400 hover:text-gray-300"
                        }`}
                    >
                        🏠 Family Groups ({circles.length})
                    </button>
                    <button
                        onClick={() => setTab("friends")}
                        className={`pb-3 px-4 font-semibold transition-all ${
                            tab === "friends"
                                ? "border-b-2 border-blue-500 text-blue-400"
                                : "text-gray-400 hover:text-gray-300"
                        }`}
                    >
                        👫 Friends ({friends.length})
                    </button>
                </div>

                {/* CIRCLES / FAMILY TAB */}
                {tab === "circles" && (
                    <div>
                        {/* Create & Join Buttons */}
                        <div className="flex gap-4 mb-8">
                            <button
                                onClick={() => setShowCreateCircle(!showCreateCircle)}
                                className="px-6 py-3 bg-red-600 hover:bg-red-700 rounded-lg font-semibold transition"
                            >
                                ➕ Create Family Group
                            </button>
                            <button
                                onClick={() => setShowJoinCircle(!showJoinCircle)}
                                className="px-6 py-3 bg-blue-600 hover:bg-blue-700 rounded-lg font-semibold transition"
                            >
                                🔗 Join Family Group
                            </button>
                        </div>

                        {/* Create Modal */}
                        {showCreateCircle && (
                            <div className="mb-6 bg-gray-800/70 backdrop-blur-sm rounded-xl p-6 border border-gray-700">
                                <h3 className="text-xl font-semibold mb-4">Create New Family Group</h3>
                                <input
                                    type="text"
                                    value={newCircleName}
                                    onChange={(e) => setNewCircleName(e.target.value)}
                                    placeholder="e.g., Smith Family, College Friends"
                                    className="w-full px-4 py-3 bg-gray-900/50 border border-gray-600 rounded-lg text-white focus:outline-none focus:border-red-500 transition mb-4"
                                />
                                <div className="flex gap-3">
                                    <button
                                        onClick={() => setShowCreateCircle(false)}
                                        className="flex-1 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg transition"
                                    >
                                        Cancel
                                    </button>
                                    <button
                                        onClick={createCircle}
                                        className="flex-1 py-2 bg-red-600 hover:bg-red-700 rounded-lg transition"
                                    >
                                        Create
                                    </button>
                                </div>
                            </div>
                        )}

                        {/* Join Modal */}
                        {showJoinCircle && (
                            <div className="mb-6 bg-gray-800/70 backdrop-blur-sm rounded-xl p-6 border border-gray-700">
                                <h3 className="text-xl font-semibold mb-4">Join Family Group</h3>
                                <input
                                    type="text"
                                    value={joinCode}
                                    onChange={(e) => setJoinCode(e.target.value.toUpperCase())}
                                    placeholder="Enter group code"
                                    className="w-full px-4 py-3 bg-gray-900/50 border border-gray-600 rounded-lg text-white text-center text-2xl font-mono tracking-widest focus:outline-none focus:border-blue-500 transition mb-4"
                                />
                                <div className="flex gap-3">
                                    <button
                                        onClick={() => setShowJoinCircle(false)}
                                        className="flex-1 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg transition"
                                    >
                                        Cancel
                                    </button>
                                    <button
                                        onClick={joinCircle}
                                        className="flex-1 py-2 bg-blue-600 hover:bg-blue-700 rounded-lg transition"
                                    >
                                        Join
                                    </button>
                                </div>
                            </div>
                        )}

                        {/* Circles List */}
                        {loading ? (
                            <div className="text-center py-12 text-gray-400">Loading...</div>
                        ) : circles.length === 0 ? (
                            <div className="bg-gray-800/50 rounded-xl p-12 text-center text-gray-400">
                                <p className="text-xl mb-2">No family groups yet</p>
                                <p className="text-sm">Create one or join an existing group</p>
                            </div>
                        ) : (
                            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                                {circles.map((circle) => (
                                    <div
                                        key={circle.id}
                                        className="bg-gray-800/70 backdrop-blur-sm rounded-xl p-6 border border-gray-700 hover:border-red-500 transition"
                                    >
                                        <div className="mb-4">
                                            <h3 className="text-2xl font-bold text-red-400 mb-2">
                                                {circle.name}
                                            </h3>
                                            <p className="text-gray-400 text-sm">
                                                {circle.member_count || 0} members
                                            </p>
                                        </div>

                                        {circle.invite_code && (
                                            <div className="bg-red-900/20 border border-red-500/30 rounded-lg p-3 mb-4">
                                                <p className="text-xs text-gray-400 mb-1">Group Code</p>
                                                <div className="flex items-center justify-between">
                                                    <code className="text-lg font-mono font-bold text-red-400">
                                                        {circle.invite_code}
                                                    </code>
                                                    <button
                                                        onClick={() => copyCode(circle.invite_code)}
                                                        className="text-gray-400 hover:text-white transition"
                                                    >
                                                        📋
                                                    </button>
                                                </div>
                                            </div>
                                        )}

                                        {circle.members && circle.members.length > 0 && (
                                            <div className="mb-4">
                                                <p className="text-xs text-gray-400 mb-2">Members:</p>
                                                <div className="flex flex-wrap gap-2">
                                                    {circle.members.map((member, idx) => (
                                                        <div
                                                            key={idx}
                                                            className="bg-gray-700/50 px-2 py-1 rounded text-xs"
                                                        >
                                                            {member.name || "Unknown"}
                                                        </div>
                                                    ))}
                                                </div>
                                            </div>
                                        )}

                                        <div className="flex gap-2">
                                            <button
                                                onClick={() => navigate("/track")}
                                                className="flex-1 py-2 bg-blue-600 hover:bg-blue-700 rounded-lg text-sm transition"
                                            >
                                                📍 View Map
                                            </button>
                                            <button
                                                onClick={() => deleteCircle(circle.id, circle.name)}
                                                className="px-3 py-2 bg-red-600/20 hover:bg-red-600 rounded-lg text-sm transition"
                                            >
                                                🗑️
                                            </button>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                )}

                {/* FRIENDS TAB */}
                {tab === "friends" && (
                    <div>
                        {/* Add Friend Button */}
                        <button
                            onClick={() => setShowAddFriend(!showAddFriend)}
                            className="px-6 py-3 bg-blue-600 hover:bg-blue-700 rounded-lg font-semibold transition mb-8"
                        >
                            ➕ Add Friend
                        </button>

                        {/* Add Friend Modal */}
                        {showAddFriend && (
                            <div className="mb-6 bg-gray-800/70 backdrop-blur-sm rounded-xl p-6 border border-gray-700">
                                <h3 className="text-xl font-semibold mb-4">Add Friend by Code</h3>
                                <input
                                    type="text"
                                    value={friendCode}
                                    onChange={(e) => setFriendCode(e.target.value.toUpperCase())}
                                    placeholder="Enter friend code"
                                    maxLength="6"
                                    className="w-full px-4 py-3 bg-gray-900/50 border border-gray-600 rounded-lg text-white text-center text-2xl font-mono tracking-widest focus:outline-none focus:border-blue-500 transition mb-4"
                                />
                                <div className="flex gap-3">
                                    <button
                                        onClick={() => setShowAddFriend(false)}
                                        className="flex-1 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg transition"
                                    >
                                        Cancel
                                    </button>
                                    <button
                                        onClick={addFriend}
                                        className="flex-1 py-2 bg-blue-600 hover:bg-blue-700 rounded-lg transition"
                                    >
                                        Add Friend
                                    </button>
                                </div>
                            </div>
                        )}

                        {/* Friends List */}
                        {friends.length === 0 ? (
                            <div className="bg-gray-800/50 rounded-xl p-12 text-center text-gray-400">
                                <p className="text-xl mb-2">No friends added yet</p>
                                <p className="text-sm">Get a friend code and add them</p>
                            </div>
                        ) : (
                            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                                {friends.map((friend) => (
                                    <div
                                        key={friend.id}
                                        className="bg-gray-800/70 backdrop-blur-sm rounded-xl p-6 border border-gray-700 hover:border-blue-500 transition"
                                    >
                                        <div className="flex items-center gap-3 mb-4">
                                            <div className="w-10 h-10 bg-gradient-to-br from-blue-500 to-purple-600 rounded-full flex items-center justify-center text-lg">
                                                👤
                                            </div>
                                            <div>
                                                <h3 className="font-semibold">
                                                    {friend.name} {friend.last_name || ""}
                                                </h3>
                                                <p className="text-xs text-gray-400">{friend.email}</p>
                                            </div>
                                        </div>

                                        <div className="flex gap-2">
                                            <button
                                                onClick={() => navigate("/track")}
                                                className="flex-1 py-2 bg-blue-600 hover:bg-blue-700 rounded-lg text-sm transition"
                                            >
                                                📍 Track
                                            </button>
                                            <button
                                                onClick={() => removeFriend(friend.id, friend.name)}
                                                className="px-3 py-2 bg-red-600/20 hover:bg-red-600 rounded-lg text-sm transition"
                                            >
                                                ❌
                                            </button>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                )}
            </div>
        </div>
    );
}
