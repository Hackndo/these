<?php
$page = $_SERVER['PHP_SELF'];
$sec = "5";
?>
<html>
    <head>
    <meta http-equiv="refresh" content="<?php echo $sec?>;URL='<?php echo $page?>'">
    </head>
    <body>
    <?php
        $response = json_decode(file_get_contents("http://localhost:5000/users"));
        print "<h1>Il y a " . $response->response . " ";
        print $response->response > 1 ? "users" : "user";
        print $response->response == 0 ? " :( " : " :D ";
        print " !</h1>";
    ?>
    </body>
</html>
