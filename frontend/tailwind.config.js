/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,jsx,ts,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: "#0f0f0f",  // Dark blackish background
        secondary: "#ff1a1a", // Red gradient accent
      },
      backgroundImage: {
        'red-black-gradient': 'linear-gradient(135deg, #0f0f0f, #ff1a1a)',
      },
    },
  },
  plugins: [],
}
