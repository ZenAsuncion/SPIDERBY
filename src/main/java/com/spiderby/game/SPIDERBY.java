package com.spiderby.game;

import com.badlogic.gdx.Game;

public class SPIDERBY extends Game {
    @Override
    public void create() {
        setScreen(new GameScreen(this));
    }

    @Override
    public void dispose() {
        if (getScreen() != null) getScreen().dispose();
    }
}