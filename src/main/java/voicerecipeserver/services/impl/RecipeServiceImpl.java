package voicerecipeserver.services.impl;

import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import voicerecipeserver.config.Constants;
import voicerecipeserver.model.dto.CommentDto;
import voicerecipeserver.model.dto.IdDto;
import voicerecipeserver.model.dto.MarkDto;
import voicerecipeserver.model.dto.RecipeDto;
import voicerecipeserver.model.entities.*;
import voicerecipeserver.model.exceptions.BadRequestException;
import voicerecipeserver.model.exceptions.NotFoundException;
import voicerecipeserver.respository.*;
import voicerecipeserver.services.RecipeService;

import java.util.*;

@Service
public class RecipeServiceImpl implements RecipeService {

    private final ModelMapper mapper;
    private final RecipeRepository recipeRepository;
    private final IngredientRepository ingredientRepository;
    private final MeasureUnitRepository measureUnitRepository;
    private final MediaRepository mediaRepository;
    private UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final MarksRepository marksRepository;
    private final AvgMarkRepository avgMarkRepository;

    @Autowired
    public RecipeServiceImpl(RecipeRepository recipeRepository, IngredientRepository ingredientRepository,
                             MeasureUnitRepository measureUnitRepository, MediaRepository mediaRepository,
                             CommentRepository commentRepository, ModelMapper mapper, MarksRepository marksRepository,
                             AvgMarkRepository avgMarkRepository) {
        this.recipeRepository = recipeRepository;
        this.ingredientRepository = ingredientRepository;
        this.measureUnitRepository = measureUnitRepository;
        this.mediaRepository = mediaRepository;
        this.marksRepository = marksRepository;
        this.avgMarkRepository = avgMarkRepository;
        this.commentRepository = commentRepository;
        this.mapper = mapper;
        this.mapper.typeMap(Recipe.class, RecipeDto.class)
                .addMappings(m ->
                    m.map(src -> src.getAuthor().getUid(), RecipeDto::setAuthorId));
        this.mapper.typeMap(Mark.class, MarkDto.class)
                .addMappings(m -> {
                    m.map(src -> src.getUser().getUid(), MarkDto::setUserUid);
                    m.map(src -> src.getRecipe().getId(), MarkDto::setRecipeId);
                });
    }

    @Autowired
    private void setUserRepository(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public ResponseEntity<RecipeDto> getRecipeById(Long id) throws NotFoundException {
        Recipe recipe = findRecipe(id);

        setAvgMark(recipe);
        RecipeDto recipeDto = mapper.map(recipe, RecipeDto.class);

        return new ResponseEntity<>(recipeDto, HttpStatus.OK);
    }

    private Recipe findRecipe(Long id) throws NotFoundException {
        Optional<Recipe> recipeOptional = recipeRepository.findById(id);
        if (recipeOptional.isEmpty()) {
            throw new NotFoundException("Не удалось найти рецепт с id: " + id);
        }
        return recipeOptional.get();
    }

    @Override
    public ResponseEntity<IdDto> addRecipe(RecipeDto recipeDto) throws NotFoundException, BadRequestException {
        Recipe recipe = mapper.map(recipeDto, Recipe.class);
        setAuthorTo(recipe);
        recipe.setId(null);

        // через маппер можно сделать путем добавления конвертера. Только вот код
        // там будет хуже, его будет сильно больше, а производительность вряд ли вырастет
        for (Step step : recipe.getSteps()) {
            step.setRecipe(recipe);
        }

        setDistribution(recipe);
        Recipe savedRecipe = recipeRepository.save(recipe);
        return new ResponseEntity<>(new IdDto().id(savedRecipe.getId()), HttpStatus.OK);
    }

    private void setAuthorTo(Recipe recipe) throws NotFoundException {
        Optional<User> author = userRepository.findByUid(recipe.getAuthor().getUid());
        if (author.isEmpty()) {
            throw new NotFoundException("Не удалось найти автора с uid: " + recipe.getAuthor().getUid());
        } else {
            recipe.setAuthor(author.get());
        }
    }


    private void setRecipeTo(Mark mark) throws NotFoundException {
        Optional<Recipe> recipe = recipeRepository.findById(mark.getRecipe().getId());
        if (recipe.isEmpty()) {
            throw new NotFoundException("Не удалось найти рецепт с id: " + mark.getRecipe().getId());
        } else {
            mark.setRecipe(recipe.get());
        }
    }

    @Override
    public ResponseEntity<IdDto> updateRecipe(RecipeDto recipeDto, Long id) throws NotFoundException, BadRequestException {
        Recipe oldRecipe = findRecipe(id);
        Recipe newRecipe = mapper.map(recipeDto, Recipe.class);
        newRecipe.setId(id);
        setAuthorTo(newRecipe);
        setSteps(oldRecipe, newRecipe);
        setDistribution(newRecipe);

        Set<Long> unusedMediaIds = getUnusedMediaIds(oldRecipe, newRecipe);

        recipeRepository.save(newRecipe);
        mediaRepository.deleteAllById(unusedMediaIds);
        return new ResponseEntity<>(new IdDto().id(newRecipe.getId()), HttpStatus.OK);
    }

    @Override
    public ResponseEntity<IdDto> addRecipeMark(MarkDto markDto) throws NotFoundException {
        Mark mark = mapper.map(markDto, Mark.class);
        setRecipeTo(mark);
        setAuthorTo(mark);
        mark.setId(null);
        marksRepository.save(mark);
        return new ResponseEntity<>(new IdDto().id(mark.getId()), HttpStatus.OK);
    }

    private Mark findMark(Long id) throws NotFoundException {
        Optional<Mark> markOptional = marksRepository.findById(id);
        if (markOptional.isEmpty()) {
            throw new NotFoundException("Не удалось найти оценку с id: " + id);
        }
        return markOptional.get();
    }

    @Override
    public ResponseEntity<IdDto> updateRecipeMark(MarkDto markDto) throws NotFoundException {
        Mark newMark = mapper.map(markDto, Mark.class);
        newMark.setId(markDto.getId());
        setAuthorTo(newMark);
        marksRepository.save(newMark);
        return new ResponseEntity<>(new IdDto().id(newMark.getId()), HttpStatus.OK);
    }

    @Override
    public ResponseEntity<Void> deleteRecipeMark(Long id) {
        marksRepository.deleteById(id);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    private void setAuthorTo(Mark mark) throws NotFoundException {
        Optional<User> author = userRepository.findByUid(mark.getUser().getUid());
        if (author.isEmpty()) {
            throw new NotFoundException("Не удалось найти автора с uid: " + mark.getUser().getUid());
        } else {
            mark.setUser(author.get());
        }
    }


    private void setDistribution(Recipe recipe) throws BadRequestException {
        HashSet<String> ingredientsInRecipe = new HashSet<>();
        for (IngredientsDistribution ingredientsDistribution : recipe.getIngredientsDistributions()) {
            ingredientsDistribution.setRecipe(recipe);

            Ingredient receivedIngredient = ingredientsDistribution.getIngredient();
            String ingredientName = receivedIngredient.getName();

            if (ingredientsInRecipe.contains(ingredientName)) {
                throw new BadRequestException("Ингредиент встречается дважды: " + ingredientName);
            }
            ingredientsInRecipe.add(receivedIngredient.getName());


            Optional<Ingredient> ingredientFromRepo = ingredientRepository.findByName(ingredientName);
            if (ingredientFromRepo.isEmpty()) {
                ingredientsDistribution.setIngredient(ingredientRepository.save(receivedIngredient));
            } else {
                ingredientsDistribution.setIngredient(ingredientFromRepo.get());
                ingredientsDistribution.setId(
                        new IngredientsDistributionKey(recipe.getId(), ingredientFromRepo.get().getId()));
            }
            //TODO в идеале бы настроить для recipe save так, чтобы он сохранял measureUnit, ingredient, если их нет
            // в БД. Хочется убрать лишний find.

            setMeasureUnit(ingredientsDistribution);
        }
    }

    private void setMeasureUnit(IngredientsDistribution ingredientsDistribution) {
        Optional<MeasureUnit> measureUnitOptional = measureUnitRepository.findByName(
                ingredientsDistribution.getUnit().getName());
        if (measureUnitOptional.isEmpty()) {
            ingredientsDistribution.setUnit(measureUnitRepository.save(ingredientsDistribution.getUnit()));
        } else {
            ingredientsDistribution.setUnit(measureUnitOptional.get());
        }
    }

    private static Set<Long> getUnusedMediaIds(Recipe oldRecipe, Recipe newRecipe) {
        Set<Long> oldRecipeMedia = getRecipeMedia(oldRecipe);
        Set<Long> newRecipeMedia = getRecipeMedia(newRecipe);
        Set<Long> unusedMedia = new HashSet<>();
        for (Long mediaId : oldRecipeMedia) {
            if (!newRecipeMedia.contains(mediaId)) {
                unusedMedia.add(mediaId);
            }
        }
        return unusedMedia;
    }

    private static void setSteps(Recipe oldRecipe, Recipe newRecipe) {
        List<Step> oldSteps = oldRecipe.getSteps();
        List<Step> newSteps = newRecipe.getSteps();
        oldSteps.sort(Comparator.comparingInt(Step::getStepNum));
        newSteps.sort(Comparator.comparingInt(Step::getStepNum));

        // rest of the oldSteps will be deleted automatically because of orphanRemoval = true
        for (int i = 0; i < newSteps.size(); ++i) {
            Step newStep = newSteps.get(i);
            if (i < oldSteps.size()) {
                newStep.setId(oldSteps.get(i).getId());
            }
            newStep.setRecipe(newRecipe);
        }
    }

    private static Set<Long> getRecipeMedia(Recipe recipe) {
        Set<Long> recipeMedia = new HashSet<>();
        recipeMedia.add(recipe.getMedia().getId());
        for (Step step : recipe.getSteps()) {
            Media media = step.getMedia();
            if (media != null) {
                recipeMedia.add(media.getId());
            }
        }
        return recipeMedia;
    }

    User findUser(String userUid) throws NotFoundException {
        Optional<User> userOptional = userRepository.findByUid(userUid);
        if (userOptional.isEmpty()) {
            throw new NotFoundException("Не удалось найти пользователя с uid: " + userUid);
        }
        return userOptional.get();
    }

    Comment findComment(Long commentId) throws NotFoundException {
        Optional<Comment> commentOptional = commentRepository.findById(commentId);
        if (commentOptional.isEmpty()) {
            throw new NotFoundException("Не удалось найти комментарий с id: " + commentId);
        }
        return commentOptional.get();
    }

    @Override
    public ResponseEntity<List<RecipeDto>> searchRecipesByName(String name, Integer limit) throws NotFoundException {
        if (limit == null) {
            limit = 0;
        }
        List<Recipe> recipes = recipeRepository.findByNameContaining(name, limit);
        for (Recipe recipe : recipes) {
            setAvgMark(recipe);
        }

        if (recipes.isEmpty()) {
            throw new NotFoundException("Не удалось найти рецепты с подстрокой: " + name);
        }
        List<RecipeDto> recipeDtos = mapper.map(recipes, new TypeToken<List<RecipeDto>>() {
        }.getType());

        return new ResponseEntity<>(recipeDtos, HttpStatus.OK);
    }

    private void setAvgMark(Recipe recipe) {
        Optional<AvgMark> avgMarkOptional = avgMarkRepository.findById(recipe.getId());
        avgMarkOptional.ifPresent(recipe::setAvgMark);
    }

    @Override
    public ResponseEntity<IdDto> postComment(CommentDto commentDto) throws NotFoundException, BadRequestException {
        Comment comment = mapper.map(commentDto, Comment.class);
        comment.setId(null);
        comment.setDate(new Date());
        User user = findUser(commentDto.getUserUid());
        Recipe recipe = findRecipe(commentDto.getRecipeId());
        comment.setUser(user);
        comment.setRecipe(recipe);

        Comment savedComment = commentRepository.save(comment);
        return new ResponseEntity<>(new IdDto().id(savedComment.getId()), HttpStatus.OK);
    }

    @Override
    public ResponseEntity<IdDto> updateComment(CommentDto commentDto) throws NotFoundException, BadRequestException {
        Comment comment = findComment(commentDto.getId());
        comment.setContent(commentDto.getContent());
        Comment savedComment = commentRepository.save(comment);
        return new ResponseEntity<>(new IdDto().id(savedComment.getId()), HttpStatus.OK);
    }

    @Override
    public ResponseEntity<Void> deleteComment(Long commentId) {
        commentRepository.deleteById(commentId);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Override
    public ResponseEntity<Void> deleteRecipe(Long recipeId) throws NotFoundException {
//        Recipe recipe = findRecipe(recipeId);
//        Media media = recipe.getMedia();
//        if (media != null && !media.getId().equals(Constants.DEFAULTMEDIAID)) {
//            mediaRepository.deleteById(media.getId());
//        }
        recipeRepository.deleteById(recipeId);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
