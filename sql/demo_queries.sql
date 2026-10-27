-- Gudotopia: queries for showing the instructor the SQLite database.
-- Open ~/.gudotopia/gudotopia.db in IntelliJ's Database tool (SQLite) or: sqlite3 ~/.gudotopia/gudotopia.db
.tables

-- 1. All saves with their money and garden size (a JOIN across two tables)
SELECT s.save_id, s.name, p.money, p.garden_size, s.last_played
FROM saves s JOIN player_data p ON p.save_id = s.save_id;

-- 2. Seeds owned by each save
SELECT s.name, f.name AS flower, i.quantity
FROM seed_inventory i JOIN saves s ON s.save_id = i.save_id JOIN flowers f ON f.flower_id = i.flower_id
WHERE i.quantity > 0 ORDER BY s.name;

-- 3. Fertilizers owned by each save
SELECT s.name, f.name AS fertilizer, i.quantity
FROM fertilizer_inventory i JOIN saves s ON s.save_id = i.save_id JOIN fertilizers f ON f.fertilizer_id = i.fertilizer_id
WHERE i.quantity > 0;

-- 4. Plots that are watered, planted or fertilized
SELECT s.name, g.plot_row, g.plot_col, g.watered, g.flower_id, g.fertilizer_id, g.death_deadline_ms
FROM garden_plots g JOIN saves s ON s.save_id = g.save_id
WHERE g.watered = 1 OR g.flower_id IS NOT NULL OR g.fertilizer_id IS NOT NULL;

-- 5. Flower collection per save
SELECT s.name, COUNT(*) AS discovered FROM flower_collection c JOIN saves s ON s.save_id = c.save_id GROUP BY s.name;

-- 6. INSERT / SELECT / UPDATE demo.
--    The game itself runs the INSERTs when you press NEW (INSERT INTO saves..., player_data..., garden_plots... in one transaction).
--    Create a save called "Demo" in the game, close the game, then run:
SELECT * FROM saves WHERE name = 'Demo';
UPDATE player_data SET money = money + 100 WHERE save_id = (SELECT save_id FROM saves WHERE name = 'Demo');
SELECT s.name, p.money FROM saves s JOIN player_data p ON p.save_id = s.save_id WHERE s.name = 'Demo';
--    Start the game again, LOAD "Demo": the extra 100 coins are there - the game really reads this database.
--    Remove the demo save (ON DELETE CASCADE deletes all its plots, seeds, fertilizers and collection rows too):
-- DELETE FROM saves WHERE name = 'Demo';
