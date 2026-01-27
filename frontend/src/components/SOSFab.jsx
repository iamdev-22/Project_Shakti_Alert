import React from "react";

export default function SOSFab({ onClick }){
  return (
    <button onClick={onClick} className="fixed bottom-6 right-6 w-16 h-16 rounded-full bg-gradient-to-br from-accent to-red-500 shadow-2xl text-white text-lg font-bold">
      SOS
    </button>
  );
}
