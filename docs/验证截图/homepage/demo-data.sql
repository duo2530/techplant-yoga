-- 首页截图用演示数据：仅用于本地 docker-compose 的开发库，日期按执行当天生成。
SET NAMES utf8mb4;
UPDATE t_coach SET level = 2, intro = '十年瑜伽教学经验，擅长体式矫正与呼吸引导',
  avatar_url = '/static/images/home/coach1-avatar.jpg' WHERE id = 1856739201475237001;
UPDATE t_coach SET level = 2, intro = NULL,
  avatar_url = '/static/images/home/coach2-avatar.jpg' WHERE id = 1856739201475237002;
UPDATE t_store SET image_url = '/static/images/store/photo-3.jpg';
UPDATE t_course SET cover_url = '/static/images/booking/cover-1.jpg';
DELETE FROM t_schedule WHERE id BETWEEN 1856739201475239901 AND 1856739201475239909;
INSERT INTO t_schedule (id, store_id, course_id, course_type, coach_id, classroom_id, schedule_date,
  start_time, end_time, max_persons, min_persons, booked_persons, status, create_by, update_by) VALUES
(1856739201475239901, 1856739201475235901, 1856739201475235801, 1, 1856739201475237001, 1856739201475236001,
  CURDATE(), CONCAT(CURDATE(), ' 23:00:00'), CONCAT(CURDATE(), ' 23:59:00'), 15, 2, 6, 2, 1, 1),
(1856739201475239902, 1856739201475235901, 1856739201475235801, 1, 1856739201475237002, 1856739201475236001,
  CURDATE(), CONCAT(CURDATE(), ' 22:30:00'), CONCAT(CURDATE(), ' 23:30:00'), 15, 2, 4, 2, 1, 1),
(1856739201475239903, 1856739201475235901, 1856739201475235802, 2, 1856739201475237002, 1856739201475236002,
  CURDATE() + INTERVAL 1 DAY, CONCAT(CURDATE() + INTERVAL 1 DAY, ' 09:00:00'), CONCAT(CURDATE() + INTERVAL 1 DAY, ' 10:15:00'), 15, 3, 9, 2, 1, 1),
(1856739201475239904, 1856739201475235901, 1856739201475235801, 1, 1856739201475237001, 1856739201475236001,
  CURDATE() + INTERVAL 2 DAY, CONCAT(CURDATE() + INTERVAL 2 DAY, ' 18:30:00'), CONCAT(CURDATE() + INTERVAL 2 DAY, ' 19:30:00'), 20, 2, 3, 2, 1, 1);
