import { game, director } from 'cc';

// Initialize the game
game.init({
    debugMode: false,
    showFPS: false,
    frameRate: 60,
    id: 'GameCanvas',
    renderMode: 0,
    jsList: []
});

// Load the first scene
director.loadScene('LoginScene');

// Start the game
game.run();