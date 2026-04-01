🛡️ Vauch Check
A lightweight, client-side utility mod to protect yourself from scammers, thieves, and griefers in multiplayer SMPs.

Vauch Check connects your Minecraft client directly to a crowdsourced moderation database. With a simple command, you can instantly check a player's trading reputation before making a deal, or submit your own reports with evidence directly from the game.

✨ Features
Fully Client-Side: You can use this on any server (DonutSMP, FyreSMP, vanilla servers, etc.). The server does not need to have the mod installed!

Instant Reputation Checks: Query the database without lagging your game. The mod uses asynchronous background fetching.

Smart Memory Cache: Checks are temporarily saved in your memory for 5 minutes, preventing API spam if you accidentally run the command twice.

In-Game Reporting: Did you just get scammed? Instantly submit a report with a URL to your video/screenshot evidence without leaving Minecraft.

Interactive Chat UI: Clickable dashboard links for server moderators to review full evidence logs.

💻 Commands & Usage
1. Check a Player

/check <username>

Example: /check Steve

Output: Displays the player's global status (Good, Vouched, Warning, or Scammer), an admin summary, and a tally of all behavioral flags.

2. Submit a Report
If you catch a player breaking the rules (or want to reward them for a good trade), you can report them to the database. You must provide a URL to your proof (YouTube, Medal, Imgur, etc.).

/scammer <username> <proof_url>

/thief <username> <proof_url>

/griefer <username> <proof_url>

/vouch <username> <proof_url>

Example: /scammer Alex https://medal.tv/games/minecraft/clips/MyProof

📥 Installation
Install Fabric Loader (version 0.16.0 or higher).

Download the correct version of Vauch Check for your Minecraft version (Supports 1.21.10 and 1.21.11).

Drop the .jar file into your mods folder.

(Optional but recommended) Install Fabric API for maximum compatibility with other mods.

🔒 Privacy & Architecture Info
For Server Admins & Curious Players:
This mod operates on a Two-Tier serverless architecture using Cloudflare Edge Workers.

When you run /check, the mod fetches a lightweight JSON payload of the player's flag counts.

Full evidence URLs and moderator notes are securely hidden behind an Admin-Auth wall on the backend, ensuring the mod cannot be exploited to leak sensitive investigation data.

When you submit a report, your Minecraft username is securely attached to the submission payload so moderators can contact you regarding your proof.

Requires: Java 21 | Environment: Client-Only
