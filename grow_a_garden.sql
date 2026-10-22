CREATE DATABASE IF NOT EXISTS grow_a_garden;
USE  grow_a_garden;
CREATE TABLE player(
player_id INT PRIMARY KEY AUTO_INCREMENT,
username VARCHAR(50) NOT NULL,
money INT DEFAULT 100,
seeds_have INT DEFAULT 0,
money_per_harvest INT DEFAULT 4,
time_to_grow INT DEFAULT 60,
extra_plots_unlocked BOOLEAN DEFAULT FALSE);


CREATE TABLE fertilizer(
player_id INT PRIMARY KEY,
price INT DEFAULT 50,
money_earn INT DEFAULT 8,
bar_width INT DEFAULT 300,
FOREIGN KEY(player_id) REFERENCES player(player_id) );


CREATE TABLE plot (
  plot_id     INT PRIMARY KEY AUTO_INCREMENT,
  player_id   INT,
  plot_name   VARCHAR(20),     
  is_occupied BOOLEAN DEFAULT FALSE,
  growth_timer FLOAT DEFAULT 0,
  growth_stage INT DEFAULT 1,  -- 1 to 6
  FOREIGN KEY (player_id) REFERENCES player(player_id)
);



INSERT INTO player (player_id, username, money, seeds_have, money_per_harvest, time_to_grow, extra_plots_unlocked)
VALUES (1121, 'Rene', 200, 50, 40, 60, TRUE);

INSERT INTO fertilizer (player_id, price, money_earn, bar_width)
VALUES (1121, 50, 8, 300);

INSERT INTO plot (player_id, plot_name, is_occupied, growth_timer, growth_stage)
VALUES
  (1121, 'st',   FALSE, 0, 1),
  (1121, 'stm1', FALSE, 0, 1),
  (1121, 'stm2', FALSE, 0, 1),
  (1121, 'ep1',  FALSE, 0, 1);

SELECT * FROM player;
SELECT * FROM fertilizer;
SELECT * FROM plot;